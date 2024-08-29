package com.afs.publicapi.utils;

public class Errors {
    public static final ResponseUtils.Error
            Ok = ResponseUtils.Error.of(0, "ok"),
            Unknown = ResponseUtils.Error.of(-1, "unknown error"),
            Internal = ResponseUtils.Error.of(1000, "internal error"),
            CompileError = ResponseUtils.Error.of(1001, "compile error"),
            InvalidJson = ResponseUtils.Error.of(1002, "invalid json"),
            InvalidParam = ResponseUtils.Error.of(1003, "invalid param"),
            ResourceNotFound = ResponseUtils.Error.of(1004, "resource not found"),
            BalanceNotEnough = ResponseUtils.Error.of(1005, "balance not enough"),
            OutOfStack = ResponseUtils.Error.of(1006, "out of stack"),
            Duplicate = ResponseUtils.Error.of(1006, "duplicated"),
            NoPermission = ResponseUtils.Error.of(1007, "no permission"),
            OperationError = ResponseUtils.Error.of(1007, "operation error"),
            NoFlowError = ResponseUtils.Error.of(1008, "current layer flow not enough")
            ;
}
