/*
 * Copyright 2002-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.context.request;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;
import org.springframework.lang.Nullable;

/**
 * Abstract {@link Scope} implementation that reads from a particular scope
 * in the current thread-bound {@link RequestAttributes} object.
 *
 * <p>Subclasses simply need to implement {@link #getScope()} to instruct
 * this class which {@link RequestAttributes} scope to read attributes from.
 *
 * <p>Subclasses may wish to override the {@link #get} and {@link #remove}
 * methods to add synchronization around the call back into this super class.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @since 2.0
 */
public abstract class AbstractRequestAttributesScope implements Scope {

    @Override
    public Object get(String name, ObjectFactory<?> objectFactory) {
        // 从 ThreadLocal 中取出 当前请求属性
        RequestAttributes attributes = RequestContextHolder.currentRequestAttributes();
        // 从 作用域中(request域、session域) 取出 name 对应的属性值
        Object scopedObject = attributes.getAttribute(name, getScope());
        if (scopedObject == null) {
            // 创建出对象
            scopedObject = objectFactory.getObject();
            // 回存作用域
            attributes.setAttribute(name, scopedObject, getScope());
            // Retrieve object again, registering it for implicit session attribute updates.
            // As a bonus, we also allow for potential decoration at the getAttribute level.
            Object retrievedObject = attributes.getAttribute(name, getScope());
            if (retrievedObject != null) {
                // Only proceed with retrieved object if still present (the expected case).
                // If it disappeared concurrently, we return our locally created instance.
                scopedObject = retrievedObject;
            }
        }
        return scopedObject;
    }

    @Override
    @Nullable
    public Object remove(String name) {
        // 从 ThreadLocal 中取出 当前请求属性
        RequestAttributes attributes = RequestContextHolder.currentRequestAttributes();
        Object scopedObject = attributes.getAttribute(name, getScope());
        if (scopedObject != null) {
            // 从作用域中移除对象
            attributes.removeAttribute(name, getScope());
            return scopedObject;
        } else {
            return null;
        }
    }

    @Override
    public void registerDestructionCallback(String name, Runnable callback) {
        RequestAttributes attributes = RequestContextHolder.currentRequestAttributes();
        attributes.registerDestructionCallback(name, callback, getScope());
    }

    @Override
    @Nullable
    public Object resolveContextualObject(String key) {
        RequestAttributes attributes = RequestContextHolder.currentRequestAttributes();
        return attributes.resolveReference(key);
    }


    /**
     * Template method that determines the actual target scope.
     * @return the target scope, in the form of an appropriate
     * {@link RequestAttributes} constant
     * @see RequestAttributes#SCOPE_REQUEST
     * @see RequestAttributes#SCOPE_SESSION
     */
    protected abstract int getScope();

}
