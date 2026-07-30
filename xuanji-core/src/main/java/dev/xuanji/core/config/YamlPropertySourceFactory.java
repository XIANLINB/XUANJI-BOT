package dev.xuanji.core.config;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertySourceFactory;

import java.util.Properties;

/**
 * YAML 属性源工厂
 *
 * <p>支持 @PropertySource 注解加载 YAML 文件。
 * 默认 @PropertySource 只支持 .properties 文件，使用此类可支持 .yml 文件。
 *
 * <h3>使用方式</h3>
 * <pre>
 * @PropertySource(value = "classpath:xxx.yml", factory = YamlPropertySourceFactory.class)
 * </pre>
 */
public class YamlPropertySourceFactory implements PropertySourceFactory {

    @Override
    public PropertySource<?> createPropertySource(String name, EncodedResource resource) {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(resource.getResource());

        Properties properties = factory.getObject();
        String sourceName = name != null ? name : resource.getResource().getFilename();

        return new PropertiesPropertySource(sourceName, properties);
    }
}
