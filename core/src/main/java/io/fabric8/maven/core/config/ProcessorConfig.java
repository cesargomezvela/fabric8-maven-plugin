/*
 * Copyright 2016 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.fabric8.maven.core.config;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Configuration for enrichers and generators
 *
 * @author roland
 * @since 24/07/16
 */
public class ProcessorConfig {

    public static final ProcessorConfig EMPTY = new ProcessorConfig();
    /**
     * Modules to includes, should holde <code>&lt;include&gt;</code> elements
     */
    @Parameter
    @JsonProperty(value = "includes")
    List<String> includes = new ArrayList<>();

    /**
     * Modules to excludes, should hold <code>&lt;exclude&gt;</code> elements
     */
    @Parameter
    @JsonProperty(value = "excludes")
    Set<String> excludes = new HashSet<>();

    /**
     * Configuration for enricher / generators
     */
    // See http://stackoverflow.com/questions/38628399/using-map-of-maps-as-maven-plugin-parameters/38642613 why
    // a "TreeMap" is used as parameter and not "Map<String, String>"
    @Parameter
    @JsonProperty(value = "config")
    Map<String, TreeMap> config = new HashMap<>();

    public ProcessorConfig() { }

    public ProcessorConfig(List<String> includes, Set<String> excludes, Map<String, TreeMap> config) {
        this.includes = includes != null ? includes : Collections.<String>emptyList();
        this.excludes = excludes != null ? excludes : Collections.<String>emptySet();
        if (config != null) {
            this.config = config;
        }
    }

    public String getConfig(String name, String key) {
        TreeMap processorMap =  config.get(name);
        return processorMap != null ? (String) processorMap.get(key) : null;
    }

    /**
     * Order elements according to the order provided by the include statements.
     * If no includes has been configured, return the given list unaltered.
     * Otherwise arrange the elements from the list in to the include order and return a new
     * list.
     *
     * If an include specifies an element which does not exist, an exception is thrown.
     *
     * @param namedList the list to order
     * @param type a description used in an error message (like 'generator' or 'enricher')
     * @param <T> the concrete type
     * @return the ordered list according to the algorithm described above
     * @throws IllegalArgumentException if the includes reference an non existing element
     */
    public <T extends Named> List<T> prepareProcessors(List<T> namedList, String type) {
        List<T> ret = new ArrayList<>();
        Map<String, T> lookup = new HashMap<>();
        for (T named : namedList) {
            lookup.put(named.getName(), named);
        }
        for (String inc : includes) {
            if (use(inc)) {
                T named = lookup.get(inc);
                if (named == null) {
                    List<String> keys = new ArrayList<>(lookup.keySet());
                    Collections.sort(keys);
                    throw new IllegalArgumentException(
                        "No " + type + " with name '" + inc +
                        "' found to include. " +
                        "Please check spelling in your profile / config and your project dependencies. Included " + type + "s: " +
                        StringUtils.join(keys,", "));
                }
                ret.add(named);
            }
        }
        return ret;
    }

    public boolean use(String inc) {
        return !excludes.contains(inc) && includes.contains(inc);
    }

    /**
     * Merge in another processor configuration, with an higher priority. This operation mutates
     * the current config.
     *
     * @param config config to merge in
     * @return this merged configuration for convenience of chaining and returning.
     */
    public static ProcessorConfig mergeProcessorConfigs(ProcessorConfig ... processorConfigs) {
        // Merge the configuration
        Map<String, TreeMap> configs = mergeConfig(processorConfigs);

        // Get all includes
        Set<String> excludes = mergeExcludes(processorConfigs);

        // Find the set of includes, which are the ones from the profile + the ones configured
        List<String> includes = mergeIncludes(processorConfigs);

        return new ProcessorConfig(includes, excludes, configs);
    }

    private static Set<String> mergeExcludes(ProcessorConfig ... configs) {
        Set<String> ret = new HashSet<>();
        for (ProcessorConfig config : configs) {
            if (config != null) {
                Set<String> excludes = config.excludes;
                if (excludes != null) {
                    ret.addAll(excludes);
                }
            }
        }
        return ret;
    }

    private static List<String> mergeIncludes(ProcessorConfig ... configs) {
        List<String> ret = new ArrayList<>();
        for (ProcessorConfig config : configs) {
            if (config != null) {
                List<String> includes = config.includes;
                if (includes != null) {
                    ret.addAll(includes);
                }
            }
        }
        return removeDups(ret);
    }

    // Remove duplicates such that the later element remains
    // Only good for small list (thats what we expect for enrichers and generators)
    private static List<String> removeDups(List<String> list) {
        List<String> ret = new ArrayList<>();
        for (int i = list.size() - 1; i >= 0; i--) {
            String el = list.get(i);
            if (!ret.contains(el)) {
                ret.add(el);
            }
        }
        Collections.reverse(ret);
        return ret;
    }

    private static Map<String, TreeMap> mergeConfig(ProcessorConfig ... processorConfigs) {
        Map<String, TreeMap> ret = new HashMap<>();
        for (ProcessorConfig processorConfig : processorConfigs) {
            if (processorConfig != null) {
                Map<String, TreeMap> config = processorConfig.config;
                if (config != null) {
                    for (Map.Entry<String, TreeMap> entry : config.entrySet()) {
                        TreeMap newValues = entry.getValue();
                        if (newValues != null) {
                            TreeMap existing = ret.get(entry.getKey());
                            if (existing == null) {
                                ret.put(entry.getKey(), new TreeMap(newValues));
                            } else {
                                for (Map.Entry newValue : (Set<Map.Entry>) newValues.entrySet()) {
                                    existing.put(newValue.getKey(), newValue.getValue());
                                }
                            }
                        }
                    }
                }
            }
        }
        return ret.size() > 0 ? ret : null;
    }

}
