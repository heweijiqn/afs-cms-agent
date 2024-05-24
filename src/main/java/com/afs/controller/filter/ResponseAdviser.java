package com.afs.controller.filter;

import com.afs.utils.ResponseUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.server.ResponseStatusException;

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
    @ExceptionHandler(value = Throwable.class)
    @ResponseStatus
    public ResponseUtils.Result exceptionHandler(Throwable t) {
        if (t instanceof ResponseStatusException error) {
            return ResponseUtils.error(error.getRawStatusCode(), error.getReason(), null, error.getRawStatusCode());
        }
        log.error("exceptionHandler internal error", t);
        if (t instanceof MaxUploadSizeExceededException) {
            return ResponseUtils.error(-1, "Maximum upload size exceeded");
        }
        return ResponseUtils.error(-1, "internal error");
    }
}
