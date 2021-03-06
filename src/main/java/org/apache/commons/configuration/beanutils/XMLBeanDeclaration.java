/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.configuration.beanutils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration.ConfigurationRuntimeException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.PropertyConverter;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.tree.ConfigurationNode;
import org.apache.commons.configuration.tree.DefaultConfigurationNode;

/**
 * <p>
 * An implementation of the {@code BeanDeclaration} interface that is
 * suitable for XML configuration files.
 * </p>
 * <p>
 * This class defines the standard layout of a bean declaration in an XML
 * configuration file. Such a declaration must look like the following example
 * fragment:
 * </p>
 * <p>
 *
 * <pre>
 *   ...
 *   &lt;personBean config-class=&quot;my.model.PersonBean&quot;
 *       lastName=&quot;Doe&quot; firstName=&quot;John&quot;&gt;
 *       &lt;address config-class=&quot;my.model.AddressBean&quot;
 *           street=&quot;21st street 11&quot; zip=&quot;1234&quot;
 *           city=&quot;TestCity&quot;/&gt;
 *   &lt;/personBean&gt;
 * </pre>
 *
 * </p>
 * <p>
 * The bean declaration can be contained in an arbitrary element. Here it is the
 * {@code personBean} element. In the attributes of this element
 * there can occur some reserved attributes, which have the following meaning:
 * <dl>
 * <dt>{@code config-class}</dt>
 * <dd>Here the full qualified name of the bean's class can be specified. An
 * instance of this class will be created. If this attribute is not specified,
 * the bean class must be provided in another way, e.g. as the
 * {@code defaultClass} passed to the {@code BeanHelper} class.</dd>
 * <dt>{@code config-factory}</dt>
 * <dd>This attribute can contain the name of the
 * {@link BeanFactory} that should be used for creating the bean.
 * If it is defined, a factory with this name must have been registered at the
 * {@code BeanHelper} class. If this attribute is missing, the default
 * bean factory will be used.</dd>
 * <dt>{@code config-factoryParam}</dt>
 * <dd>With this attribute a parameter can be specified that will be passed to
 * the bean factory. This may be useful for custom bean factories.</dd>
 * </dl>
 * </p>
 * <p>
 * All further attributes starting with the {@code config-} prefix are
 * considered as meta data and will be ignored. All other attributes are treated
 * as properties of the bean to be created, i.e. corresponding setter methods of
 * the bean will be invoked with the values specified here.
 * </p>
 * <p>
 * If the bean to be created has also some complex properties (which are itself
 * beans), their values cannot be initialized from attributes. For this purpose
 * nested elements can be used. The example listing shows how an address bean
 * can be initialized. This is done in a nested element whose name must match
 * the name of a property of the enclosing bean declaration. The format of this
 * nested element is exactly the same as for the bean declaration itself, i.e.
 * it can have attributes defining meta data or bean properties and even further
 * nested elements for complex bean properties.
 * </p>
 * <p>
 * A {@code XMLBeanDeclaration} object is usually created from a
 * {@code HierarchicalConfiguration}. From this it will derive a
 * {@code SubnodeConfiguration}, which is used to access the needed
 * properties. This subnode configuration can be obtained using the
 * {@link #getConfiguration()} method. All of its properties can
 * be accessed in the usual way. To ensure that the property keys used by this
 * class are understood by the configuration, the default expression engine will
 * be set.
 * </p>
 *
 * @since 1.3
 * @author <a
 * href="http://commons.apache.org/configuration/team-list.html">Commons
 * Configuration team</a>
 * @version $Id: XMLBeanDeclaration.java 1301959 2012-03-17 16:43:18Z oheger $
 */
public class XMLBeanDeclaration implements BeanDeclaration
{
    /** Constant for the prefix of reserved attributes. */
    public static final String RESERVED_PREFIX = "config-";

    /** Constant for the prefix for reserved attributes.*/
    public static final String ATTR_PREFIX = "[@" + RESERVED_PREFIX;

    /** Constant for the bean class attribute. */
    public static final String ATTR_BEAN_CLASS = ATTR_PREFIX + "class]";

    /** Constant for the bean factory attribute. */
    public static final String ATTR_BEAN_FACTORY = ATTR_PREFIX + "factory]";

    /** Constant for the bean factory parameter attribute. */
    public static final String ATTR_FACTORY_PARAM = ATTR_PREFIX
            + "factoryParam]";

    /** Stores the associated configuration. */
    private final SubnodeConfiguration configuration;

    /** Stores the configuration node that contains the bean declaration. */
    private final ConfigurationNode node;

    /**
     * Creates a new instance of {@code XMLBeanDeclaration} and
     * initializes it from the given configuration. The passed in key points to
     * the bean declaration.
     *
     * @param config the configuration
     * @param key the key to the bean declaration (this key must point to
     * exactly one bean declaration or a {@code IllegalArgumentException}
     * exception will be thrown)
     */
    public XMLBeanDeclaration(HierarchicalConfiguration config, String key)
    {
        this(config, key, false);
    }

    /**
     * Creates a new instance of {@code XMLBeanDeclaration} and
     * initializes it from the given configuration. The passed in key points to
     * the bean declaration. If the key does not exist and the boolean argument
     * is <b>true</b>, the declaration is initialized with an empty
     * configuration. It is possible to create objects from such an empty
     * declaration if a default class is provided. If the key on the other hand
     * has multiple values or is undefined and the boolean argument is <b>false</b>,
     * a {@code IllegalArgumentException} exception will be thrown.
     *
     * @param config the configuration
     * @param key the key to the bean declaration
     * @param optional a flag whether this declaration is optional; if set to
     * <b>true</b>, no exception will be thrown if the passed in key is
     * undefined
     */
    public XMLBeanDeclaration(HierarchicalConfiguration config, String key,
            boolean optional)
    {
        if (config == null)
        {
            throw new IllegalArgumentException(
                    "Configuration must not be null!");
        }

        SubnodeConfiguration tmpconfiguration = null;
        ConfigurationNode tmpnode = null;
        try
        {
            tmpconfiguration = config.configurationAt(key);
            tmpnode = tmpconfiguration.getRootNode();
        }
        catch (IllegalArgumentException iex)
        {
            // If we reach this block, the key does not have exactly one value
            if (!optional || config.getMaxIndex(key) > 0)
            {
                throw iex;
            }
            tmpconfiguration = config.configurationAt(null);
            tmpnode = new DefaultConfigurationNode();
        }
        this.node = tmpnode;
        this.configuration = tmpconfiguration;
        initSubnodeConfiguration(getConfiguration());
    }

    /**
     * Creates a new instance of {@code XMLBeanDeclaration} and
     * initializes it from the given configuration. The configuration's root
     * node must contain the bean declaration.
     *
     * @param config the configuration with the bean declaration
     */
    public XMLBeanDeclaration(HierarchicalConfiguration config)
    {
        this(config, (String) null);
    }

    /**
     * Creates a new instance of {@code XMLBeanDeclaration} and
     * initializes it with the configuration node that contains the bean
     * declaration.
     *
     * @param config the configuration
     * @param node the node with the bean declaration.
     */
    public XMLBeanDeclaration(SubnodeConfiguration config,
            ConfigurationNode node)
    {
        if (config == null)
        {
            throw new IllegalArgumentException(
                    "Configuration must not be null!");
        }
        if (node == null)
        {
            throw new IllegalArgumentException("Node must not be null!");
        }

        this.node = node;
        configuration = config;
        initSubnodeConfiguration(config);
    }

    /**
     * Returns the configuration object this bean declaration is based on.
     *
     * @return the associated configuration
     */
    public SubnodeConfiguration getConfiguration()
    {
        return configuration;
    }

    /**
     * Returns the node that contains the bean declaration.
     *
     * @return the configuration node this bean declaration is based on
     */
    public ConfigurationNode getNode()
    {
        return node;
    }

    /**
     * Returns the name of the bean factory. This information is fetched from
     * the {@code config-factory} attribute.
     *
     * @return the name of the bean factory
     */
    public String getBeanFactoryName()
    {
        return getConfiguration().getString(ATTR_BEAN_FACTORY);
    }

    /**
     * Returns a parameter for the bean factory. This information is fetched
     * from the {@code config-factoryParam} attribute.
     *
     * @return the parameter for the bean factory
     */
    public Object getBeanFactoryParameter()
    {
        return getConfiguration().getProperty(ATTR_FACTORY_PARAM);
    }

    /**
     * Returns the name of the class of the bean to be created. This information
     * is obtained from the {@code config-class} attribute.
     *
     * @return the name of the bean's class
     */
    public String getBeanClassName()
    {
        return getConfiguration().getString(ATTR_BEAN_CLASS);
    }

    /**
     * Returns a map with the bean's (simple) properties. The properties are
     * collected from all attribute nodes, which are not reserved.
     *
     * @return a map with the bean's properties
     */
    public Map<String, Object> getBeanProperties()
    {
        Map<String, Object> props = new HashMap<String, Object>();
        for (ConfigurationNode attr : getNode().getAttributes())
        {
            if (!isReservedNode(attr))
            {
                props.put(attr.getName(), interpolate(attr .getValue()));
            }
        }

        return props;
    }

    /**
     * Returns a map with bean declarations for the complex properties of the
     * bean to be created. These declarations are obtained from the child nodes
     * of this declaration's root node.
     *
     * @return a map with bean declarations for complex properties
     */
    public Map<String, Object> getNestedBeanDeclarations()
    {
        Map<String, Object> nested = new HashMap<String, Object>();
        for (ConfigurationNode child : getNode().getChildren())
        {
            if (!isReservedNode(child))
            {
                if (nested.containsKey(child.getName()))
                {
                    Object obj = nested.get(child.getName());
                    List<BeanDeclaration> list;
                    if (obj instanceof List)
                    {
                        // Safe because we created the lists ourselves.
                        @SuppressWarnings("unchecked")
                        List<BeanDeclaration> tmpList = (List<BeanDeclaration>) obj;
                        list = tmpList;
                    }
                    else
                    {
                        list = new ArrayList<BeanDeclaration>();
                        list.add((BeanDeclaration) obj);
                        nested.put(child.getName(), list);
                    }
                    list.add(createBeanDeclaration(child));
                }
                else
                {
                    nested.put(child.getName(), createBeanDeclaration(child));
                }
            }
        }

        return nested;
    }

    /**
     * Performs interpolation for the specified value. This implementation will
     * interpolate against the current subnode configuration's parent. If sub
     * classes need a different interpolation mechanism, they should override
     * this method.
     *
     * @param value the value that is to be interpolated
     * @return the interpolated value
     */
    protected Object interpolate(Object value)
    {
        return PropertyConverter.interpolate(value, getConfiguration()
                .getParent());
    }

    /**
     * Checks if the specified node is reserved and thus should be ignored. This
     * method is called when the maps for the bean's properties and complex
     * properties are collected. It checks whether the given node is an
     * attribute node and if its name starts with the reserved prefix.
     *
     * @param nd the node to be checked
     * @return a flag whether this node is reserved (and does not point to a
     * property)
     */
    protected boolean isReservedNode(ConfigurationNode nd)
    {
        return nd.isAttribute()
                && (nd.getName() == null || nd.getName().startsWith(
                        RESERVED_PREFIX));
    }

    /**
     * Creates a new {@code BeanDeclaration} for a child node of the
     * current configuration node. This method is called by
     * {@code getNestedBeanDeclarations()} for all complex sub properties
     * detected by this method. Derived classes can hook in if they need a
     * specific initialization. This base implementation creates a
     * {@code XMLBeanDeclaration} that is properly initialized from the
     * passed in node.
     *
     * @param node the child node, for which a {@code BeanDeclaration} is
     *        to be created
     * @return the {@code BeanDeclaration} for this child node
     * @since 1.6
     */
    protected BeanDeclaration createBeanDeclaration(ConfigurationNode node)
    {
        List<HierarchicalConfiguration> list = getConfiguration().configurationsAt(node.getName());
        if (list.size() == 1)
        {
            return new XMLBeanDeclaration((SubnodeConfiguration) list.get(0), node);
        }
        else
        {
            Iterator<HierarchicalConfiguration> iter = list.iterator();
            while (iter.hasNext())
            {
                SubnodeConfiguration config = (SubnodeConfiguration) iter.next();
                if (config.getRootNode().equals(node))
                {
                    return new XMLBeanDeclaration(config, node);
                }
            }
            throw new ConfigurationRuntimeException("Unable to match node for " + node.getName());
        }
    }

    /**
     * Initializes the internally managed subnode configuration. This method
     * will set some default values for some properties.
     *
     * @param conf the configuration to initialize
     */
    private void initSubnodeConfiguration(SubnodeConfiguration conf)
    {
        conf.setThrowExceptionOnMissing(false);
        conf.setExpressionEngine(null);
    }
}
