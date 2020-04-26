package com.jzzdev.lazysql.data;

/**
 * @author Zsj
 */
public class LazySqlResult<E> {
    private boolean error;
    private E result;

    public LazySqlResult() {

    }

    public LazySqlResult(boolean error, E result) {
        this.error = error;
        this.result = result;
    }

    public boolean isError() {
        return error;
    }

    public E getResult() {
        return result;
    }

    @Override
    public String toString() {
        return "LazySqlResult{" +
                "error=" + error +
                ", result=" + result +
                '}';
    }
}
