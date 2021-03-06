package org.platformlayer.service.tomcat.ops;

import java.io.IOException;

import org.apache.log4j.Logger;
import org.platformlayer.ops.Handler;
import org.platformlayer.ops.OpsContext;
import org.platformlayer.ops.OpsException;
import org.platformlayer.ops.instances.DiskImageRecipeBuilder;
import org.platformlayer.ops.instances.InstanceBuilder;
import org.platformlayer.ops.java.JavaVirtualMachine;
import org.platformlayer.ops.metrics.collectd.CollectdCollector;
import org.platformlayer.ops.metrics.collectd.ManagedService;
import org.platformlayer.ops.packages.PackageDependency;
import org.platformlayer.ops.tree.OpsTreeBase;
import org.platformlayer.service.tomcat.model.TomcatService;

public class TomcatServiceController extends OpsTreeBase {
    static final Logger log = Logger.getLogger(TomcatServiceController.class);

    @Handler
    public void doOperation() throws OpsException, IOException {
    }

    @Override
    protected void addChildren() throws OpsException {
        TomcatService model = OpsContext.get().getInstance(TomcatService.class);

        InstanceBuilder instance = InstanceBuilder.build(model.dnsName, DiskImageRecipeBuilder.buildDiskImageRecipe(this));
        instance.minimumMemoryMb = 2048;
        addChild(instance);

        instance.addChild(JavaVirtualMachine.buildJava6());

        instance.addChild(PackageDependency.build("libtcnative-1"));
        instance.addChild(PackageDependency.build("tomcat6"));
        // tomcat6-admin contains the 'manager' app for remote deploys
        instance.addChild(PackageDependency.build("tomcat6-admin"));

        instance.addChild(TomcatUsers.build());

        instance.addChild(TomcatServerBootstrap.build());

        instance.addChild(CollectdCollector.build());

        instance.addChild(ManagedService.build("tomcat6"));
    }
}
