package com.afs.publicapi.controller.filter;

import com.afs.publicapi.utils.ResponseUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.server.ResponseStatusException;

@ControllerAdvice(basePackages = "com.afs.publicapi.controller")
@Slf4j
public class ResponseAdviser implements ResponseBodyAdvice<Object> {

    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    public Object beforeBodyWrite(
        Object body,
        MethodParameter returnType,
        MediaType selectedContentType,
        Class<? extends HttpMessageConverter<?>> selectedConverterType,
        ServerHttpRequest request,
        ServerHttpResponse response) {
        log.debug("request path={}", request.getURI());
        if (body instanceof ResponseUtils.Result) {
            ResponseUtils.Result r = (ResponseUtils.Result) body;
            if (r.httpStatus != 0) {
                response.setStatusCode(HttpStatus.resolve(r.httpStatus));
            }
            return r;
        } else if (body instanceof ResponseUtils.Error) {
            ResponseUtils.Result r =  ResponseUtils.error((ResponseUtils.Error)body);
            if (r.httpStatus != 0) {
                response.setStatusCode(HttpStatus.resolve(r.httpStatus));
            }
            return r;
        } else if (body instanceof ResponseUtils.RawResult) {
            return ((ResponseUtils.RawResult) body).data;
        } else if (body instanceof String) {
            return body;
        } else {
            return ResponseUtils.result(body);
        }
    }

    @ResponseBody
    @ExceptionHandler(value = ResponseUtils.Error.class)
    @ResponseStatus
    public ResponseUtils.Result definedErrorHandler(Throwable t) {
        log.error("defined internal error", t);
        ResponseUtils.Error error = (ResponseUtils.Error)t;
        return ResponseUtils.error(error);
    }

    @ResponseBody
    @ExceptionHandler(value = ResponseStatusException.class)
    public ResponseUtils.Result responseStatus(ResponseStatusException error) {
        return ResponseUtils.error(error.getRawStatusCode(), error.getReason(), null, error.getRawStatusCode());
    }

    @ResponseBody
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public ResponseUtils.Result invalidBody(MethodArgumentNotValidException error) {
        String message = error.getBindingResult().getFieldErrors().stream()
                .findFirst().map(field -> field.getField() + ": " + field.getDefaultMessage())
                .orElse("请求参数校验失败");
        return ResponseUtils.error(400, message, null, 400);
    }

    @ResponseBody
    @ExceptionHandler(value = MissingServletRequestParameterException.class)
    public ResponseUtils.Result missingParameter(MissingServletRequestParameterException error) {
        return ResponseUtils.error(400, "缺少请求参数: " + error.getParameterName(), null, 400);
    }

    @ResponseBody
    @ExceptionHandler(value = MissingRequestHeaderException.class)
    public ResponseUtils.Result missingHeader(MissingRequestHeaderException error) {
        return ResponseUtils.error(400, "缺少请求头: " + error.getHeaderName(), null, 400);
    }

    @ResponseBody
    @ExceptionHandler(value = MethodArgumentTypeMismatchException.class)
    public ResponseUtils.Result invalidType(MethodArgumentTypeMismatchException error) {
        return ResponseUtils.error(400, "参数格式不正确: " + error.getName(), null, 400);
    }

    @ResponseBody
    @ExceptionHandler(value = HttpMessageNotReadableException.class)
    public ResponseUtils.Result unreadableBody(HttpMessageNotReadableException error) {
        return ResponseUtils.error(400, "请求体格式不正确", null, 400);
    }

    @ResponseBody
    @ExceptionHandler(value = Throwable.class)
    @ResponseStatus
    public ResponseUtils.Result exceptionHandler(Throwable t) {
        if (!(t instanceof ClientAbortException)) {
            log.error("internal error", t);
        }
        return ResponseUtils.error(-1, "internal error");
    }
}
