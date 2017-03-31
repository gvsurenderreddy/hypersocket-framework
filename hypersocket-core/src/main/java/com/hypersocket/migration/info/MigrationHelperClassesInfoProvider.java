package com.hypersocket.migration.info;

import com.hypersocket.migration.exporter.MigrationExporter;
import com.hypersocket.migration.importer.MigrationImporter;
import com.hypersocket.migration.order.MigrationOrder;
import com.hypersocket.repository.AbstractEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;

@Component
public class MigrationHelperClassesInfoProvider {

    static Logger log = LoggerFactory.getLogger(MigrationHelperClassesInfoProvider.class);

    @Autowired
    ApplicationContext applicationContext;

    private Map<Short, List<Class<? extends AbstractEntity<Long>>>> migrationOrderMap = new TreeMap<>();
    private Map<Class<?>, MigrationImporter> migrationImporterMap = new HashMap<>();
    private Map<Class<?>, MigrationExporter> migrationExporterMap = new HashMap<>();

    @PostConstruct
    private void postConstruct() {
        scanForMigrationHelperClasses();
    }

    public Map<Short, List<Class<? extends AbstractEntity<Long>>>> getMigrationOrderMap() {
        return Collections.unmodifiableMap(migrationOrderMap);
    }

    public Map<Class<?>, MigrationImporter> getMigrationImporterMap() {
        return Collections.unmodifiableMap(migrationImporterMap);
    }

    public Map<Class<?>, MigrationExporter> getMigrationExporterMap() {
        return Collections.unmodifiableMap(migrationExporterMap);
    }

    private void scanForMigrationHelperClasses() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(MigrationOrder.class));
        scanner.addIncludeFilter(new AssignableTypeFilter(MigrationImporter.class));
        scanner.addIncludeFilter(new AssignableTypeFilter(MigrationExporter.class));

        final Set<BeanDefinition> classes = scanner.findCandidateComponents("com.hypersocket.migration");

        try {
            for (BeanDefinition beanDefinition : classes) {
                String className = beanDefinition.getBeanClassName();
                Class aClass = MigrationHelperClassesInfoProvider.class.getClassLoader().loadClass(className);
                if(MigrationOrder.class.isAssignableFrom(aClass)) {
                    MigrationOrder instance = (MigrationOrder) aClass.newInstance();
                    migrationOrderMap.put(instance.sortOrder(), instance.getOrderList());
                } else if (MigrationImporter.class.isAssignableFrom(aClass)) {
                    MigrationImporter instance = (MigrationImporter) applicationContext.getBean(aClass.getCanonicalName());
                    migrationImporterMap.put(instance.getType(), instance);
                } else if (MigrationExporter.class.isAssignableFrom(aClass)) {
                    MigrationExporter instance = (MigrationExporter) applicationContext.getBean(aClass.getCanonicalName());
                    migrationExporterMap.put(instance.getType(), instance);
                }
            }
        }catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
}