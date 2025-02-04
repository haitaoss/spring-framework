/*
 * Copyright 2002-2021 the original author or authors.
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

package org.springframework.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.env.AbstractPropertyResolver;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.lang.Nullable;

import java.util.*;

/**
 * Utility class for working with Strings that have placeholder values in them.
 * A placeholder takes the form {@code ${name}}. Using {@code PropertyPlaceholderHelper}
 * these placeholders can be substituted for user-supplied values.
 *
 * <p>Values for substitution can be supplied using a {@link Properties} instance or
 * using a {@link PlaceholderResolver}.
 *
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @since 3.0
 */
public class PropertyPlaceholderHelper {

    private static final Log logger = LogFactory.getLog(PropertyPlaceholderHelper.class);

    private static final Map<String, String> wellKnownSimplePrefixes = new HashMap<>(4);

    static {
        wellKnownSimplePrefixes.put("}", "{");
        wellKnownSimplePrefixes.put("]", "[");
        wellKnownSimplePrefixes.put(")", "(");
    }


    private final String placeholderPrefix;

    private final String placeholderSuffix;

    private final String simplePrefix;

    @Nullable
    private final String valueSeparator;

    private final boolean ignoreUnresolvablePlaceholders;


    /**
     * Creates a new {@code PropertyPlaceholderHelper} that uses the supplied prefix and suffix.
     * Unresolvable placeholders are ignored.
     * @param placeholderPrefix the prefix that denotes the start of a placeholder
     * @param placeholderSuffix the suffix that denotes the end of a placeholder
     */
    public PropertyPlaceholderHelper(String placeholderPrefix, String placeholderSuffix) {
        this(placeholderPrefix, placeholderSuffix, null, true);
    }

    /**
     * Creates a new {@code PropertyPlaceholderHelper} that uses the supplied prefix and suffix.
     * @param placeholderPrefix the prefix that denotes the start of a placeholder
     * @param placeholderSuffix the suffix that denotes the end of a placeholder
     * @param valueSeparator the separating character between the placeholder variable
     * and the associated default value, if any
     * @param ignoreUnresolvablePlaceholders indicates whether unresolvable placeholders should
     * be ignored ({@code true}) or cause an exception ({@code false})
     */
    public PropertyPlaceholderHelper(String placeholderPrefix, String placeholderSuffix,
                                     @Nullable String valueSeparator, boolean ignoreUnresolvablePlaceholders) {

        Assert.notNull(placeholderPrefix, "'placeholderPrefix' must not be null");
        Assert.notNull(placeholderSuffix, "'placeholderSuffix' must not be null");
        this.placeholderPrefix = placeholderPrefix;
        this.placeholderSuffix = placeholderSuffix;
        // 使用 后缀 找预先支持的前缀
        String simplePrefixForSuffix = wellKnownSimplePrefixes.get(this.placeholderSuffix);
        // 命中了前缀 且 设置的前缀是以命中的前缀结尾的
        if (simplePrefixForSuffix != null && this.placeholderPrefix.endsWith(simplePrefixForSuffix)) {
            // 作为简单的前缀
            this.simplePrefix = simplePrefixForSuffix;
        } else {
            this.simplePrefix = this.placeholderPrefix;
        }
        this.valueSeparator = valueSeparator;
        this.ignoreUnresolvablePlaceholders = ignoreUnresolvablePlaceholders;
    }


    /**
     * Replaces all placeholders of format {@code ${name}} with the corresponding
     * property from the supplied {@link Properties}.
     * @param value the value containing the placeholders to be replaced
     * @param properties the {@code Properties} to use for replacement
     * @return the supplied value with placeholders replaced inline
     */
    public String replacePlaceholders(String value, final Properties properties) {
        Assert.notNull(properties, "'properties' must not be null");
        return replacePlaceholders(value, properties::getProperty);
    }

    /**
     * Replaces all placeholders of format {@code ${name}} with the value returned
     * from the supplied {@link PlaceholderResolver}.
     * @param value the value containing the placeholders to be replaced
     * @param placeholderResolver the {@code PlaceholderResolver} to use for replacement
     * @return the supplied value with placeholders replaced inline
     */
    public String replacePlaceholders(String value, PlaceholderResolver placeholderResolver) {
        Assert.notNull(value, "'value' must not be null");
        // 解析字符串的值
        return parseStringValue(value, placeholderResolver, null);
    }

    protected String parseStringValue(
            String value, PlaceholderResolver placeholderResolver, @Nullable Set<String> visitedPlaceholders) {

        // 字符串没有 前缀占位符 直接返回
        int startIndex = value.indexOf(this.placeholderPrefix);
        if (startIndex == -1) {
            return value;
        }

        StringBuilder result = new StringBuilder(value);
        while (startIndex != -1) {
            // 返回 后缀的索引
            int endIndex = findPlaceholderEndIndex(result, startIndex);
            // 存在后缀的情况
            if (endIndex != -1) {
                // 拿到占位符
                String placeholder = result.substring(startIndex + this.placeholderPrefix.length(), endIndex);
                String originalPlaceholder = placeholder;
                if (visitedPlaceholders == null) {
                    visitedPlaceholders = new HashSet<>(4);
                }
                if (!visitedPlaceholders.add(originalPlaceholder)) {
                    throw new IllegalArgumentException(
                            "Circular placeholder reference '" + originalPlaceholder + "' in property definitions");
                }
                /**
                 * 递归解析，拿到占位符的值。
                 * 比如 ${name},拿到的是name对应的属性值，也就是支持 属性文件(yml、properties) 中支持使用占位符引用属性
                 * */
                // Recursive invocation, parsing placeholders contained in the placeholder key.
                placeholder = parseStringValue(placeholder, placeholderResolver, visitedPlaceholders);
                /**
                 * 获取属性值
                 * {@link AbstractPropertyResolver#doResolvePlaceholders(String, PropertyPlaceholderHelper)}
                 * {@link PropertySourcesPropertyResolver#getPropertyAsRawString(String)}
                 * */
                // Now obtain the value for the fully resolved key...
                String propVal = placeholderResolver.resolvePlaceholder(placeholder);
                /**
                 * 解析的值为null 且 设置了值分隔符，说明可能是 ${name:default_name} 这种写法,
                 * 所以要按照 : 分割后，使用name再次获取属性值
                 * */
                if (propVal == null && this.valueSeparator != null) {
                    /**
                     * 比如 ${name:default_name}
                     * */
                    int separatorIndex = placeholder.indexOf(this.valueSeparator);
                    if (separatorIndex != -1) {
                        // 拿到的是属性名 name
                        String actualPlaceholder = placeholder.substring(0, separatorIndex);
                        // 拿到的是 default_name，作为默认值
                        String defaultValue = placeholder.substring(separatorIndex + this.valueSeparator.length());
                        /**
                         * 获取属性值
                         * */
                        propVal = placeholderResolver.resolvePlaceholder(actualPlaceholder);
                        // 解析不到属性值，就使用默认值作为其值
                        if (propVal == null) {
                            propVal = defaultValue;
                        }
                    }
                }
                if (propVal != null) {
                    // 递归调用。也就是支持属性值也是占位符的情况
                    // Recursive invocation, parsing placeholders contained in the
                    // previously resolved placeholder value.
                    propVal = parseStringValue(propVal, placeholderResolver, visitedPlaceholders);
                    // 从原有字符串中替换掉占位符
                    result.replace(startIndex, endIndex + this.placeholderSuffix.length(), propVal);
                    if (logger.isTraceEnabled()) {
                        logger.trace("Resolved placeholder '" + placeholder + "'");
                    }
                    // 拿到下一个占位符的下标
                    startIndex = result.indexOf(this.placeholderPrefix, startIndex + propVal.length());
                } else if (this.ignoreUnresolvablePlaceholders) {
                    // Proceed with unprocessed value.
                    startIndex = result.indexOf(this.placeholderPrefix, endIndex + this.placeholderSuffix.length());
                } else {
                    throw new IllegalArgumentException("Could not resolve placeholder '" +
                            placeholder + "'" + " in value \"" + value + "\"");
                }
                visitedPlaceholders.remove(originalPlaceholder);
            } else {
                startIndex = -1;
            }
        }
        // 返回字符串
        return result.toString();
    }

    private int findPlaceholderEndIndex(CharSequence buf, int startIndex) {
        int index = startIndex + this.placeholderPrefix.length();
        int withinNestedPlaceholder = 0;
        while (index < buf.length()) {
            if (StringUtils.substringMatch(buf, index, this.placeholderSuffix)) {
                if (withinNestedPlaceholder > 0) {
                    withinNestedPlaceholder--;
                    index = index + this.placeholderSuffix.length();
                } else {
                    return index;
                }
            } else if (StringUtils.substringMatch(buf, index, this.simplePrefix)) {
                withinNestedPlaceholder++;
                index = index + this.simplePrefix.length();
            } else {
                index++;
            }
        }
        return -1;
    }


    /**
     * Strategy interface used to resolve replacement values for placeholders contained in Strings.
     */
    @FunctionalInterface
    public interface PlaceholderResolver {

        /**
         * Resolve the supplied placeholder name to the replacement value.
         * @param placeholderName the name of the placeholder to resolve
         * @return the replacement value, or {@code null} if no replacement is to be made
         */
        @Nullable
        String resolvePlaceholder(String placeholderName);
    }

}
