package com.afs.controller.filter;

import com.afs.utils.ResponseUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.server.ResponseStatusException;

import javax.validation.ConstraintViolationException;
import java.util.stream.Collectors;

@ControllerAdvice(basePackages = {"com.afs.controller", "com.afs.agent"})
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
        log.debug("beforeBodyWrite path={}", request.getURI());

        if (MediaType.TEXT_EVENT_STREAM.isCompatibleWith(selectedContentType)) {
            return body;
        }
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
        log.error("definedErrorHandler internal error", t);
        ResponseUtils.Error error = (ResponseUtils.Error)t;
        return ResponseUtils.error(error);
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
    @ExceptionHandler(value = BindException.class)
    public ResponseUtils.Result invalidBinding(BindException error) {
        String message = error.getBindingResult().getFieldErrors().stream()
                .findFirst().map(field -> field.getField() + ": " + field.getDefaultMessage())
                .orElse("请求参数校验失败");
        return ResponseUtils.error(400, message, null, 400);
    }

    @ResponseBody
    @ExceptionHandler(value = ConstraintViolationException.class)
    public ResponseUtils.Result invalidConstraint(ConstraintViolationException error) {
        String message = error.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining("; "));
        return ResponseUtils.error(400, message.isBlank() ? "请求参数校验失败" : message, null, 400);
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
    @ExceptionHandler(value = HttpRequestMethodNotSupportedException.class)
    public ResponseUtils.Result unsupportedMethod(HttpRequestMethodNotSupportedException error) {
        return ResponseUtils.error(405, "不支持的请求方法: " + error.getMethod(), null, 405);
    }

    @ResponseBody
    @ExceptionHandler(value = MaxUploadSizeExceededException.class)
    public ResponseUtils.Result uploadTooLarge(MaxUploadSizeExceededException error) {
        return ResponseUtils.error(413, "上传文件超过大小限制", null, 413);
    }

    @ResponseBody
    @ExceptionHandler(value = Throwable.class)
    @ResponseStatus
    public ResponseUtils.Result exceptionHandler(Throwable t) {
        if (t instanceof ResponseStatusException error) {
            return ResponseUtils.error(error.getRawStatusCode(), error.getReason(), null, error.getRawStatusCode());
        }
        log.error("exceptionHandler internal error", t);
        return ResponseUtils.error(-1, "internal error");
    }
}
