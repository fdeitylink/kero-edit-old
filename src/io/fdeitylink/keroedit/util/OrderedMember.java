package io.fdeitylink.keroedit.util;

import java.lang.annotation.Documented;

import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Documented
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.CONSTRUCTOR, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface OrderedMember {
    int value();
}