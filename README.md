# LazySQL
懒人SQL福利，无需担心不时的```SqlException```，专注于眼下代码的开发。


### 使用方法

#### Step 1

在Maven项目根目录新建```lib```文件夹，将```lazy-sql-x.x-SNAPSHOT.jar```复制进去。

项目结构如下：

```
|-- ProjectDir
	|-- lib
	|	|-- lazy-sql-x.x-SNAPSHOP.jar
	|-- src
	|	|-- main
	|		|-- java
	|-- pom.xml
```

#### Step 2

在```pom.xml```文件中添加依赖。

```xml
<dependencies>
    ...
    <dependency>
        <groupId>com.jzzdev</groupId>
        <artifactId>lazy-sql</artifactId>
        <version>1.0-SNAPSHOT</version>
        <scope>system</scope>
        <systemPath>${project.basedir}/lib/lazy-sql-1.0-SNAPSHOT.jar</systemPath>
    </dependency>
</dependencies>
```

并开启Maven编译插件。

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.8.0</version>
            <configuration>
                <source>1.8</source>
                <target>1.8</target>
                <compilerArgs>
                    <arg>-verbose</arg>
                    <arg>-Xlint:all</arg>
                </compilerArgs>
                <compilerArguments>
                    <extdirs>${project.basedir}/lib</extdirs>
                </compilerArguments>
                <forceJavacCompilerUse>true</forceJavacCompilerUse>
                <encoding>utf-8</encoding>

                <annotationProcessorPaths>
                    <path>
                        <groupId>com.jzzdev</groupId>
                        <artifactId>lazy-sql</artifactId>
                        <version>1.0-SNAPSHOT</version>
                    </path>
                </annotationProcessorPaths>

                <annotationProcessors>
                    com.jzzdev.lazysql.processors.LazySqlProcessor
                </annotationProcessors>
            </configuration>
        </plugin>
    </plugins>
</build>
```

#### Step 3

在类或接口上添加```@LazySql```注解开启。

```java
@LazySql
public interface TestInterface {

    int[] getItem();

    List<MyType> getItem2();

}
```

在项目中使用生成的方法。

```java
public class App {

    private static TestInterface testInterface = new TestInterface() {
        @Override
        public int[] getItem() {
            return new int[] { 6 };
        }

        @Override
        public List<MyType> getItem2() {
            return null;
        }
    };

    public static void main(String[] args) {
        System.out.println(testInterface.lazyGetItem());
    }

}
```

#### Step 4

使用```mvn clean package```命令打包后您将得到以下```TestInterface.class```文件

```java
import com.jzzdev.lazysql.data.LazySqlResult;
import java.util.List;

public interface TestInterface {
    default LazySqlResult<?> lazyGetItem2() {
        try {
            List<MyType> result = this.getItem2();
            return new LazySqlResult(false, result);
        } catch (Exception var2) {
            return new LazySqlResult(true, var2);
        }
    }

    default LazySqlResult<?> lazyGetItem() {
        try {
            int[] result = this.getItem();
            return new LazySqlResult(false, result);
        } catch (Exception var2) {
            return new LazySqlResult(true, var2);
        }
    }

    int[] getItem();

    List<MyType> getItem2();
}
```

#### Step 5

前往项目根目录，并在命令行中使用命令：

```bash
java -classpath "../lib/lazy-sql-1.0-SNAPSHOT.jar;lazy-sql-test-1.0-SNAPSHOT.jar" org.example.App
```

来测试。

期望得到以下输出：

```bash
LazySqlResult{error=false, result=[I@4554617c}
```



### 未来功能

- [ ] 添加原生获取（不使用```LazySqlResult```类包裹返回值）的支持



### 提出建议

如果遇到了BUG，或有更好的建议，前往[Issues](https://github.com/nullptrjzz/LazySQL/issues)页面提交Issue！



### 贡献代码

欢迎各路大佬对项目进行代码补充，并提交[Pull Requests](https://github.com/nullptrjzz/LazySQL/pulls)！
