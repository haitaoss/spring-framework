/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.validation.beanvalidation;

import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.Iterator;
import java.util.Set;

/**
 * Simple {@link BeanPostProcessor} that checks JSR-303 constraint annotations
 * in Spring-managed beans, throwing an initialization exception in case of
 * constraint violations right before calling the bean's init method (if any).
 *
 * @author Juergen Hoeller
 * @since 3.0
 */
public class BeanValidationPostProcessor implements BeanPostProcessor, InitializingBean {

	@Nullable
	private Validator validator;

	private boolean afterInitialization = false;


	/**
	 * Set the JSR-303 Validator to delegate to for validating beans.
	 * <p>Default is the default ValidatorFactory's default Validator.
	 */
	public void setValidator(Validator validator) {
		this.validator = validator;
	}

	/**
	 * Set the JSR-303 ValidatorFactory to delegate to for validating beans,
	 * using its default Validator.
	 * <p>Default is the default ValidatorFactory's default Validator.
	 * @see javax.validation.ValidatorFactory#getValidator()
	 */
	public void setValidatorFactory(ValidatorFactory validatorFactory) {
		this.validator = validatorFactory.getValidator();
	}

	/**
	 * Choose whether to perform validation after bean initialization
	 * (i.e. after init methods) instead of before (which is the default).
	 * <p>Default is "false" (before initialization). Switch this to "true"
	 * (after initialization) if you would like to give init methods a chance
	 * to populate constrained fields before they get validated.
	 */
	public void setAfterInitialization(boolean afterInitialization) {
		this.afterInitialization = afterInitialization;
	}

	@Override
	public void afterPropertiesSet() {
		if (this.validator == null) {
			this.validator = Validation.buildDefaultValidatorFactory().getValidator();
		}
	}


	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		if (!this.afterInitialization) {
			doValidate(bean);
		}
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		if (this.afterInitialization) {
			doValidate(bean);
		}
		return bean;
	}


	/**
	 * Perform validation of the given bean.
	 * @param bean the bean instance to validate
	 * @see javax.validation.Validator#validate
	 */
	protected void doValidate(Object bean) {
		Assert.state(this.validator != null, "No Validator set");
		// 拿到被代理类
		Object objectToValidate = AopProxyUtils.getSingletonTarget(bean);
		// 拿不到就是用入参
		if (objectToValidate == null) {
			objectToValidate = bean;
		}
		/**
		 * 借助 Validator 校验bean的属性
		 *
		 * Tips：可以看看 hibernate-validator 怎么用，就知道这里的意思了
		 * */
		Set<ConstraintViolation<Object>> result = this.validator.validate(objectToValidate);

		// 不是空，说明有不满足的信息，直接报错
		if (!result.isEmpty()) {
			StringBuilder sb = new StringBuilder("Bean state is invalid: ");
			for (Iterator<ConstraintViolation<Object>> it = result.iterator(); it.hasNext();) {
				ConstraintViolation<Object> violation = it.next();
				sb.append(violation.getPropertyPath()).append(" - ").append(violation.getMessage());
				if (it.hasNext()) {
					sb.append("; ");
				}
			}
			throw new BeanInitializationException(sb.toString());
		}
	}

}
