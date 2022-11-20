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

package org.springframework.context.annotation;

import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.Profiles;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.MultiValueMap;

/**
 * {@link Condition} that matches based on the value of a {@link Profile @Profile}
 * annotation.
 *
 * @author Chris Beams
 * @author Phillip Webb
 * @author Juergen Hoeller
 * @since 4.0
 */
class ProfileCondition implements Condition {

	@Override
	public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
		MultiValueMap<String, Object> attrs = metadata.getAllAnnotationAttributes(Profile.class.getName());
		// 有 @Profile 注解
		if (attrs != null) {
			// 遍历
			for (Object value : attrs.get("value")) {
				/**
				 * 匹配了，就返回true
				 * {@link AbstractEnvironment#acceptsProfiles(Profiles)}
				 *
				 * Profiles.of 有点复杂，没看懂
				 * */
				if (context.getEnvironment().acceptsProfiles(Profiles.of((String[]) value))) {
					return true;
				}
			}
			// 一个都不匹配，就返回false
			return false;
		}
		// 没有 @Profile注解 直接返回 true
		return true;
	}

}
