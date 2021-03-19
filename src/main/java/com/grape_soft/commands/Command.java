package com.grape_soft.commands;

@java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE})
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@java.lang.annotation.Documented
@org.springframework.stereotype.Component
public @interface Command {
    @org.springframework.core.annotation.AliasFor(annotation = org.springframework.stereotype.Component.class)
    java.lang.String value() default "";
    java.lang.String id();

    boolean proxyBeanMethods() default true;
}
