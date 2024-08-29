package com.afs.publicapi.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class ResponseUtils {
    public static class Error extends RuntimeException {
        final public int code;
        final public String msg;
        final public int httpStatus;
        public Error(int code, String msg, int httpStatus) {
            super(msg);
            this.code = code;
            this.msg = msg;
            this.httpStatus = httpStatus;
        }

        public Error(int code, String msg) {
            super(msg);
            this.code = code;
            this.msg = msg;
            this.httpStatus = 200;
        }

        public static Error of(int code, String msg, int httpStatus) {
            return new Error(code, msg, httpStatus);
        }

        public static Error of(int code, String msg) {
            return new Error(code, msg);
        }
    }

    public static class Result {
        public transient int httpStatus = 200;
        public int code = 0;
        public String msg = "";
        public Object data;
    }

    public static class RawResult {
        public Object data;
    }

    public static Result result(final Object data) {
        Result result = new Result();
        result.code = 0;
        if (data instanceof List || (data != null && data.getClass().isArray())) {
            Map<String, Object> m = new HashMap<String, Object>();
            m.put("list", data);
            result.data = m;
        } else if (data instanceof Collection) {
            Map<String, Object> m = new HashMap<String, Object>();
            m.put("list", new ArrayList<Object>((Collection) data));
            result.data = m;
        } else {
            result.data = data;
        }
        return result;
    }

    public static RawResult rawResult(Object data) {
        RawResult result = new RawResult();
        result.data = data;
        return result;
    }

    public static Result ok() {
        return error(Errors.Ok.code, Errors.Ok.msg, new HashMap<>(), 200);
    }

    public static Result error(Error error) {
        return error(error.code, error.msg, null, error.httpStatus);
    }

    public static Result error(Error error, String message) {
        return error(error.code, message, null, error.httpStatus);
    }

    public static Result error(int code, String msg) {
        return error(code, msg, new HashMap<>(), 200);
    }

    public static Result error(Error error, Object data) {
        return error(error.code, error.msg, data, error.httpStatus);
    }

    public static Result error(int code, String msg, Object data) {
        return error(code, msg, data, 200);
    }

    public static Result error(int code, String msg, Object data, int httpStatus) {
        Result result = new Result();
        result.code = code;
        result.msg = msg;
        result.data = data;
        if (httpStatus != 0) {
            result.httpStatus = httpStatus;
        }
        return result;
    }
}
