package com.afs.controller.filter;

import com.afs.dao.model.User;
import lombok.Data;

public class KiwiContext {

    private static ThreadLocal<Context> kiwiContext = new ThreadLocal<>();

    public static void setContext(Context context) {
        kiwiContext.set(context);
    }

    public static void clear() {
        kiwiContext.remove();
    }

    public static User getUser() {
        return kiwiContext.get().getUser();
    }

    public static Context getContext() {
        return kiwiContext.get();
    }

    @Data
    public static class Context {
        private User user;
    }
}
