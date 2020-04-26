package com.jzzdev.lazysql.processors;

import com.google.auto.service.AutoService;
import com.jzzdev.lazysql.annotations.LazySql;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.BoundKind;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.Set;

@SupportedAnnotationTypes("com.jzzdev.lazysql.annotations.LazySql")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@AutoService(Processor.class)
public class LazySqlProcessor extends AbstractProcessor {
    private Messager messager;
    private JavacTrees trees;
    private TreeMaker treeMaker;
    private Names names;
    private JavacElements elements;

    private static final String LAZY_SQL_RESULT = "com.jzzdev.lazysql.data.LazySqlResult";

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        this.trees = JavacTrees.instance(processingEnv);
        Context context = ((JavacProcessingEnvironment) processingEnv).getContext();
        this.treeMaker = TreeMaker.instance(context);
        this.names = Names.instance(context);
        this.elements =
                ((JavacProcessingEnvironment) processingEnv).getElementUtils();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<? extends Element> set = roundEnv.getElementsAnnotatedWith(LazySql.class);
        set.forEach(element -> {
            JCTree jcTree = trees.getTree(element);
            jcTree.accept(new TreeTranslator() {
                @Override
                public void visitClassDef(JCTree.JCClassDecl jcClassDecl) {
                    List<JCTree.JCMethodDecl> jcMethodDeclList = List.nil();

                    boolean isInterface = Tree.Kind.INTERFACE.equals(jcClassDecl.getKind());

                    for (JCTree tree : jcClassDecl.defs) {
                        if (tree.getKind().equals(Tree.Kind.METHOD)) {
                            treeMaker.at(jcClassDecl.pos());
                            JCTree.JCMethodDecl jcMethodDecl = (JCTree.JCMethodDecl) tree;
                            JCTree returnType = ((JCTree.JCMethodDecl) tree).getReturnType();
                            if (returnType != null && !"void".equals(returnType.type.toString())) {
                                jcMethodDeclList = jcMethodDeclList.append(jcMethodDecl);
                            }
                        }
                    }

                    jcMethodDeclList.forEach(jcMethodDecl -> {
                        messager.printMessage(Diagnostic.Kind.NOTE, jcMethodDecl.getName() + " has been processed");
                        jcClassDecl.defs = jcClassDecl.defs.prepend(makeLazySqlMethod(jcMethodDecl, isInterface));
                    });

                    super.visitClassDef(jcClassDecl);
                }

            });
        });

        return true;
    }

    /**
    * public LazySqlResult<String> lazyGetVal() {
        try {
            Object result = getVal();
            return new LazySqlResult<String>(false, (String) result);
        } catch (Exception e) {
            return new LazySqlResult<>(true, e);
        }
    }
    * */
    private JCTree.JCMethodDecl makeLazySqlMethod(JCTree.JCMethodDecl jcMethodDecl, boolean isInterface) {
        ListBuffer<JCTree.JCStatement> statements = new ListBuffer<>();
        JCTree.JCBlock normalStatement = normalGet(jcMethodDecl);
        
        // Catches
        List<JCTree.JCCatch> catches = List.of(
            getCatchBlock(new Exception())
        );

        statements.append(
                treeMaker.Try(
                        normalStatement, catches, null
                )
        );

        JCTree.JCBlock body = treeMaker.Block(0, statements.toList());

        return treeMaker.MethodDef(
                treeMaker.Modifiers(isInterface ? Flags.DEFAULT : Flags.PUBLIC),
                getNewMethodName(jcMethodDecl.getName()),
                treeMaker.TypeApply(memberAccess(LAZY_SQL_RESULT), List.of(treeMaker.Wildcard(treeMaker.TypeBoundKind(BoundKind.UNBOUND), null))),
                List.nil(),
                jcMethodDecl.params,
                List.nil(), body, null);
    }

    private JCTree.JCCatch getCatchBlock(Exception e) {
        String name = "lazySql" + e.getClass().getSimpleName();

        JCTree.JCIdent eIdent = treeMaker.Ident(elements.getTypeElement(e.getClass().getName()));
        JCTree.JCVariableDecl var = treeMaker.Param(names.fromString(name), eIdent.type, eIdent.sym);

        JCTree.JCNewClass newClass = treeMaker.NewClass(
                null, List.nil(),
                treeMaker.TypeApply(memberAccess(LAZY_SQL_RESULT), List.of(memberAccess(e.getClass().getName()))),
                List.of(treeMaker.Literal(true), treeMaker.Ident(names.fromString(name))), null
        );

        // return new LazySqlResult<T>(false, result);
        JCTree.JCReturn jcReturn = treeMaker.Return(newClass);
        return treeMaker.Catch(var, treeMaker.Block(0, List.of(jcReturn)));
    }

    private JCTree.JCBlock normalGet(JCTree.JCMethodDecl jcMethodDecl) {
        // T result = handle();
        JCTree.JCVariableDecl varResult = makeVarDef(
                treeMaker.Modifiers(0), "result", jcMethodDecl.restype,
                treeMaker.Exec(
                        treeMaker.Apply(
                                List.nil(),
                                memberAccess("this." + jcMethodDecl.name),
                                getMethodParams(jcMethodDecl)
                        )
                ).expr
        );

        JCTree.JCNewClass newClass = treeMaker.NewClass(
                null, List.nil(),
                treeMaker.TypeApply(memberAccess(LAZY_SQL_RESULT), List.of( jcMethodDecl.restype )),
                List.of(treeMaker.Literal(false), treeMaker.Ident(names.fromString("result"))), null
        );

        // return new LazySqlResult<T>(false, result);
        JCTree.JCReturn jcReturn = treeMaker.Return(newClass);
        return treeMaker.Block(0, List.of(varResult, jcReturn));
    }

    private List<JCTree.JCExpression> getMethodParamTypes(JCTree.JCMethodDecl jcMethodDecl) {
        List<JCTree.JCVariableDecl> vars = jcMethodDecl.getParameters();
        java.util.List<JCTree.JCExpression> tmpExp = new ArrayList<>();
        vars.forEach(var -> tmpExp.add(var.vartype));
        return List.from(tmpExp);
    }

    private List<JCTree.JCExpression> getMethodParams(JCTree.JCMethodDecl jcMethodDecl) {
        List<JCTree.JCVariableDecl> vars = jcMethodDecl.getParameters();
        java.util.List<JCTree.JCExpression> tmpExp = new ArrayList<>();
        vars.forEach(var -> {
            tmpExp.add(treeMaker.Ident(var));
        });
        return List.from(tmpExp);
    }

    private JCTree.JCExpression memberAccess(String components) {
        String[] componentArray = components.split("\\.");
        JCTree.JCExpression expr = treeMaker.Ident(names.fromString(componentArray[0]));
        for (int i = 1; i < componentArray.length; i++) {
            expr = treeMaker.Select(expr, names.fromString(componentArray[i]));
        }
        return expr;
    }

    private JCTree.JCVariableDecl makeVarDef(JCTree.JCModifiers modifiers, String name, JCTree.JCExpression vartype, JCTree.JCExpression init) {
        return treeMaker.VarDef(
                modifiers,
                names.fromString(name),
                vartype,
                init
        );
    }

    private Name getNewMethodName(Name name) {
        String s = name.toString();
        return names.fromString("lazy" + s.substring(0, 1).toUpperCase() + s.substring(1));
    }

}
