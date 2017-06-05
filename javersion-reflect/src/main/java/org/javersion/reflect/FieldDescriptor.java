/*
 * Copyright 2013 Samppa Saarela
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.javersion.reflect;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import javax.annotation.Nonnull;

import org.javersion.util.Check;

public final class FieldDescriptor extends JavaMemberDescriptor<Field> implements AccessibleProperty {

    @Nonnull
    private final Field field;

    public FieldDescriptor(TypeDescriptor typeDescriptor, Field field) {
        super(typeDescriptor);
        this.field = Check.notNull(field, "field");
        field.setAccessible(true);
    }

    public Object getStatic() {
        return get(null);
    }

    public Object get(Object obj) {
        try {
            return field.get(obj);
        } catch (IllegalAccessException e) {
            throw new ReflectionException(toString(), e);
        }
    }

    @Override
    public boolean isReadable() {
        return true;
    }

    @Override
    public boolean isReadableFrom(TypeDescriptor typeDescriptor) {
        return typeDescriptor.isSubTypeOf(field.getDeclaringClass());
    }

    @Override
    public boolean isWritable() {
        return true;
    }

    @Override
    public boolean isWritableFrom(TypeDescriptor typeDescriptor) {
        return typeDescriptor.isSubTypeOf(field.getDeclaringClass());
    }

    public void setStatic(Object value) {
        set(null, value);
    }

    public void set(Object obj, Object value) {
        try {
            field.set(obj, value);
        } catch (IllegalArgumentException | IllegalAccessException e) {
            throw new ReflectionException(toString(), e);
        }
    }

    public boolean isTransient() {
        return Modifier.isTransient(field.getModifiers());
    }

    public TypeDescriptor getType() {
        return resolveType(field.getGenericType());
    }

    @Override
    public Field getElement() {
        return field;
    }

    public final int hashCode() {
        return 31 * getDeclaringType().hashCode() + field.hashCode();
    }

    public final boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (obj instanceof FieldDescriptor) {
            FieldDescriptor other = (FieldDescriptor) obj;
            return field.equals(other.field) &&
                    this.declaringType.equals(other.declaringType);
        } else {
            return false;
        }
    }

    public String getName() {
        return field.getName();
    }

    public String toString() {
        return field.getDeclaringClass().getCanonicalName() + "." + getName();
    }

}