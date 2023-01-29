/*
 * Copyright 2002-2020 the original author or authors.
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

package org.springframework.web.method.annotation;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.core.Conventions;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpSessionRequiredException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.support.InvocableHandlerMethod;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Assist with initialization of the {@link Model} before controller method
 * invocation and with updates to it after the invocation.
 *
 * <p>On initialization the model is populated with attributes temporarily stored
 * in the session and through the invocation of {@code @ModelAttribute} methods.
 *
 * <p>On update model attributes are synchronized with the session and also
 * {@link BindingResult} attributes are added if missing.
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public final class ModelFactory {

    private static final Log logger = LogFactory.getLog(ModelFactory.class);


    /**
     * 记录的是 @ModelAttribute 对应的方法
     */
    private final List<ModelMethod> modelMethods = new ArrayList<>();

    private final WebDataBinderFactory dataBinderFactory;

    /**
     * 处理类上的@SessionAttributes
     */
    private final SessionAttributesHandler sessionAttributesHandler;


    /**
     * Create a new instance with the given {@code @ModelAttribute} methods.
     *
     * @param handlerMethods   the {@code @ModelAttribute} methods to invoke
     * @param binderFactory    for preparation of {@link BindingResult} attributes
     * @param attributeHandler for access to session attributes
     */
    public ModelFactory(@Nullable List<InvocableHandlerMethod> handlerMethods, WebDataBinderFactory binderFactory,
                        SessionAttributesHandler attributeHandler) {

        if (handlerMethods != null) {
            /**
             * 遍历，这些方法都是有@ModelAttribute的
             * {@link org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter#getModelFactory(HandlerMethod, WebDataBinderFactory)}
             * */
            for (InvocableHandlerMethod handlerMethod : handlerMethods) {
                /**
                 * ModelMethod 实例化时 会记录方法参数上是否有 @ModelAttribute 注解
                 *
                 * Tips：所以说只有方法上存在 @ModelAttribute 注解，才会额外解析方法参数上的@ModelAttribute
                 * */
                this.modelMethods.add(new ModelMethod(handlerMethod));
            }
        }
        this.dataBinderFactory = binderFactory;
        this.sessionAttributesHandler = attributeHandler;
    }


    /**
     * Populate the model in the following order:
     * <ol>
     * <li>Retrieve "known" session attributes listed as {@code @SessionAttributes}.
     * <li>Invoke {@code @ModelAttribute} methods
     * <li>Find {@code @ModelAttribute} method arguments also listed as
     * {@code @SessionAttributes} and ensure they're present in the model raising
     * an exception if necessary.
     * </ol>
     *
     * @param request       the current request
     * @param container     a container with the model to be initialized
     * @param handlerMethod the method for which the model is initialized
     * @throws Exception may arise from {@code @ModelAttribute} methods
     */
    public void initModel(NativeWebRequest request, ModelAndViewContainer container,
                          HandlerMethod handlerMethod) throws Exception {
        /**
         * SessionAttributesHandler
         * 根据类上的@SessionAttributes(name={"name1","name2"}) 从session域中获取对应的属性值，
         * 使用 sessionAttributesHandler 的目的是可以对 name增加统一的前缀，在读session域
         *
         * 注：HandlerMethod 映射的是具体的一个Method的，所以此时已经知道属于哪个类了
         * */
        Map<String, ?> sessionAttributes = this.sessionAttributesHandler.retrieveAttributes(request);
        // 合并属性信息，不存在才put进去
        container.mergeAttributes(sessionAttributes);
        /**
         * 处理 标注了@ModelAttribute的方法(全局+本类的)
         *
         * 若 @ModelAttribute("name") name 在 container 中没有，那就
         * 回调@ModelAttribute标注的方法，并将方法的返回值存到 container 中
         *  container.addAttribute(name,returnVal)
         *
         * */
        invokeModelAttributeMethods(request, container);
        /**
         * 遍历方法参数列表，若@ModelAttribute的 name 或者 paramType 在 @SessionAttributes 中有定义，
         * 就返回这个name集合
         * */
        for (String name : findSessionAttributeArguments(handlerMethod)) {
            if (!container.containsAttribute(name)) {
                // 从 session 域中获取属性值
                Object value = this.sessionAttributesHandler.retrieveAttribute(request, name);
                if (value == null) {
                    throw new HttpSessionRequiredException("Expected session attribute '" + name + "'", name);
                }
                // 扩展到 container 中
                container.addAttribute(name, value);
            }
        }
    }

    /**
     * Invoke model attribute methods to populate the model.
     * Attributes are added only if not already present in the model.
     */
    private void invokeModelAttributeMethods(NativeWebRequest request,
                                             ModelAndViewContainer container) throws Exception {

        // 实例化的时候初始化了这个属性，是根据 @ModelAttribute 来设置的
        while (!this.modelMethods.isEmpty()) {
            // 拿到方法信息，每获取一次就 modelMethods.remove
            InvocableHandlerMethod modelMethod = getNextModelMethod(container).getHandlerMethod();
            // 拿到方法上的注解
            ModelAttribute ann = modelMethod.getMethodAnnotation(ModelAttribute.class);
            Assert.state(ann != null, "No ModelAttribute annotation");
            // 存在这个属性
            if (container.containsAttribute(ann.name())) {
                // 不需要绑定
                if (!ann.binding()) {
                    // 将这个属性记录为 禁用
                    container.setBindingDisabled(ann.name());
                }
                // 跳过本轮循环
                continue;
            }

            // 执行 @ModelAttribute 标注的方法
            Object returnValue = modelMethod.invokeForRequest(request, container);
            // 方法的返回值是 void
            if (modelMethod.isVoid()) {
                if (StringUtils.hasText(ann.value())) {
                    if (logger.isDebugEnabled()) {
                        logger.debug(
                                "Name in @ModelAttribute is ignored because method returns void: " + modelMethod.getShortLogMessage());
                    }
                }
                continue;
            }
            /**
             * 会使用方法上的 @ModelAttribute("extend_attr") 注解值 作为 returnValueName，若没有指定注解值那就是返回值类型的短名称作为 returnValueName
             * {@link cn.haitaoss.controller.MyControllerAdvice#ModelAttribute(String, String)}
             * */
            String returnValueName = getNameForReturnValue(returnValue, modelMethod.getReturnType());
            if (!ann.binding()) {
                // 将这个属性记录为 禁用
                container.setBindingDisabled(returnValueName);
            }
            if (!container.containsAttribute(returnValueName)) {
                // 添加这个属性信息到 container 中
                container.addAttribute(returnValueName, returnValue);
            }
        }
    }

    private ModelMethod getNextModelMethod(ModelAndViewContainer container) {
        // 先获取存在属性的，然后再处理缺少属性的
        for (ModelMethod modelMethod : this.modelMethods) {
            // container 中 有需要的属性信息，就会是true
            if (modelMethod.checkDependencies(container)) {
                // 移除掉
                this.modelMethods.remove(modelMethod);
                // 返回
                return modelMethod;
            }
        }
        ModelMethod modelMethod = this.modelMethods.get(0);
        this.modelMethods.remove(modelMethod);
        return modelMethod;
    }

    /**
     * Find {@code @ModelAttribute} arguments also listed as {@code @SessionAttributes}.
     */
    private List<String> findSessionAttributeArguments(HandlerMethod handlerMethod) {
        List<String> result = new ArrayList<>();
        for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
            // 存在 @ModelAttribute
            if (parameter.hasParameterAnnotation(ModelAttribute.class)) {
                String name = getNameForParameter(parameter);
                Class<?> paramType = parameter.getParameterType();
                // name 或者 paramType 在 @SessionAttributes 中有定义
                if (this.sessionAttributesHandler.isHandlerSessionAttribute(name, paramType)) {
                    // 记录起来
                    result.add(name);
                }
            }
        }
        return result;
    }

    /**
     * Promote model attributes listed as {@code @SessionAttributes} to the session.
     * Add {@link BindingResult} attributes where necessary.
     *
     * @param request   the current request
     * @param container contains the model to update
     * @throws Exception if creating BindingResult attributes fails
     */
    public void updateModel(NativeWebRequest request, ModelAndViewContainer container) throws Exception {
        ModelMap defaultModel = container.getDefaultModel();
        // 会话完成了
        if (container.getSessionStatus()
                .isComplete()) {
            // 从request域中移除属性
            this.sessionAttributesHandler.cleanupAttributes(request);
        } else {
            // 存到session域中(将@SessionAttributes)关注的属性 存到session域中
            this.sessionAttributesHandler.storeAttributes(request, defaultModel);
        }
        /**
         * 请求未处理完(就是需要做视图的解析)  && 相等(说明没有 RedirectAttributes 类型的参数)
         * {@link org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter#getModelAndView(ModelAndViewContainer, ModelFactory, NativeWebRequest)}
         *
         * 如果 HandlerMethod 的参数中有 RedirectAttributes 类型的， 会影响到 container.getModel()
         * {@link org.springframework.web.servlet.mvc.method.annotation.RedirectAttributesMethodArgumentResolver}
         * */
        if (!container.isRequestHandled() && container.getModel() == defaultModel) {
            /**
             * 遍历 defaultModel 中的key 是@SessionAttributes中的  或者 不是常见的数据类型 就
             * 构造出 WebDataBinder dataBinder = this.dataBinderFactory.createBinder(request, value, name);
             * 然后存回 defaultModel 中
             *
             * 注：目的是触发 @InitBinder 方法的回调吗？目前只能猜到这个用处
             * */
            updateBindingResult(request, defaultModel);
        }
    }

    /**
     * Add {@link BindingResult} attributes to the model for attributes that require it.
     */
    private void updateBindingResult(NativeWebRequest request, ModelMap model) throws Exception {
        List<String> keyNames = new ArrayList<>(model.keySet());
        for (String name : keyNames) {
            Object value = model.get(name);
            /**
             * 是候选的（ 是@SessionAttributes中的  或者 不是常见的数据类型）
             * {@link cn.haitaoss.controller.RequestMappingHandlerMapping.HelloController#index7(Model)}
             * */
            if (value != null && isBindingCandidate(name, value)) {
                String bindingResultKey = BindingResult.MODEL_KEY_PREFIX + name;
                // 不存在这个key
                if (!model.containsAttribute(bindingResultKey)) {
                    // 会回调 @InitBinder 方法
                    WebDataBinder dataBinder = this.dataBinderFactory.createBinder(request, value, name);
                    // 设置
                    model.put(bindingResultKey, dataBinder.getBindingResult());
                }
            }
        }
    }

    /**
     * Whether the given attribute requires a {@link BindingResult} in the model.
     */
    private boolean isBindingCandidate(String attributeName, Object value) {
        if (attributeName.startsWith(BindingResult.MODEL_KEY_PREFIX)) {
            return false;
        }

        // 是 @SessionAttributes 中的属性
        if (this.sessionAttributesHandler.isHandlerSessionAttribute(attributeName, value.getClass())) {
            return true;
        }

        // 不是数组 && 不是集合 && 不是Map && 不是简单值类型
        return (!value.getClass()
                .isArray() && !(value instanceof Collection) && !(value instanceof Map) && !BeanUtils.isSimpleValueType(
                value.getClass()));
    }


    /**
     * Derive the model attribute name for the given method parameter based on
     * a {@code @ModelAttribute} parameter annotation (if present) or falling
     * back on parameter type based conventions.
     *
     * @param parameter a descriptor for the method parameter
     * @return the derived name
     * @see Conventions#getVariableNameForParameter(MethodParameter)
     */
    public static String getNameForParameter(MethodParameter parameter) {
        ModelAttribute ann = parameter.getParameterAnnotation(ModelAttribute.class);
        String name = (ann != null ? ann.value() : null);
        return (StringUtils.hasText(name) ? name : Conventions.getVariableNameForParameter(parameter));
    }

    /**
     * Derive the model attribute name for the given return value. Results will be
     * based on:
     * <ol>
     * <li>the method {@code ModelAttribute} annotation value
     * <li>the declared return type if it is more specific than {@code Object}
     * <li>the actual return value type
     * </ol>
     *
     * @param returnValue the value returned from a method invocation
     * @param returnType  a descriptor for the return type of the method
     * @return the derived name (never {@code null} or empty String)
     */
    public static String getNameForReturnValue(@Nullable Object returnValue, MethodParameter returnType) {
        // 类上的 @ModelAttribute 的name
        ModelAttribute ann = returnType.getMethodAnnotation(ModelAttribute.class);
        if (ann != null && StringUtils.hasText(ann.value())) {
            return ann.value();
        } else {
            // 比如 返回值类型是 String ，return 的是 string
            Method method = returnType.getMethod();
            Assert.state(method != null, "No handler method");
            Class<?> containingClass = returnType.getContainingClass();
            Class<?> resolvedType = GenericTypeResolver.resolveReturnType(method, containingClass);
            return Conventions.getVariableNameForReturnType(method, resolvedType, returnValue);
        }
    }


    private static class ModelMethod {

        private final InvocableHandlerMethod handlerMethod;

        private final Set<String> dependencies = new HashSet<>();

        public ModelMethod(InvocableHandlerMethod handlerMethod) {
            this.handlerMethod = handlerMethod;
            for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
                // 方法参数有注解
                if (parameter.hasParameterAnnotation(ModelAttribute.class)) {
                    // 记录起来
                    this.dependencies.add(getNameForParameter(parameter));
                }
            }
        }

        public InvocableHandlerMethod getHandlerMethod() {
            return this.handlerMethod;
        }

        public boolean checkDependencies(ModelAndViewContainer mavContainer) {
            for (String name : this.dependencies) {
                if (!mavContainer.containsAttribute(name)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public String toString() {
            return this.handlerMethod.getMethod()
                    .toGenericString();
        }
    }

}
