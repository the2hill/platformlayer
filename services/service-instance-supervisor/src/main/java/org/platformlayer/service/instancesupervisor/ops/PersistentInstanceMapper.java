package org.platformlayer.service.instancesupervisor.ops;

import java.io.IOException;

import javax.inject.Inject;

import org.platformlayer.PlatformLayerClient;
import org.platformlayer.core.model.InstanceBase;
import org.platformlayer.core.model.PlatformLayerKey;
import org.platformlayer.core.model.Tag;
import org.platformlayer.core.model.TagChanges;
import org.platformlayer.core.model.Tags;
import org.platformlayer.crypto.OpenSshUtils;
import org.platformlayer.ops.CloudContext;
import org.platformlayer.ops.Handler;
import org.platformlayer.ops.Machine;
import org.platformlayer.ops.MachineCreationRequest;
import org.platformlayer.ops.OpsContext;
import org.platformlayer.ops.OpsException;
import org.platformlayer.ops.OpsProvider;
import org.platformlayer.ops.OpsSystem;
import org.platformlayer.ops.machines.PlatformLayerCloudHelpers;
import org.platformlayer.ops.tagger.Tagger;
import org.platformlayer.ops.tree.OpsTreeBase;
import org.platformlayer.service.instancesupervisor.model.PersistentInstance;

import com.google.common.collect.Lists;

public class PersistentInstanceMapper extends OpsTreeBase {
    @Inject
    CloudContext cloud;

    @Inject
    PlatformLayerClient platformLayer;

    @Inject
    PlatformLayerCloudHelpers cloudHelpers;

    @Handler
    public void handler(PersistentInstance model) throws OpsException {
        Machine machine = null;

        Tag tagForInstance = OpsSystem.get().createParentTag(model);
        boolean instanceIsTagged = false;

        // See if we have an instance id tag
        {
            String instanceKey = model.getTags().findUnique(Tag.INSTANCE_KEY);
            if (instanceKey != null) {
                InstanceBase foundInstance = cloud.findInstanceByInstanceKey(PlatformLayerKey.parse(instanceKey));
                if (foundInstance == null) {
                    throw new IllegalStateException("Tagged with instance id, but instance not found: " + instanceKey);
                }
                machine = cloudHelpers.toMachine(foundInstance);
                if (machine == null) {
                    throw new IllegalStateException();
                }

                instanceIsTagged = true;
            }
        }

        if (machine == null) {
            // We may have created a machine, but failed to tag the instance
            machine = cloud.findMachine(tagForInstance);
        }

        if (!OpsContext.isDelete()) {
            if (machine == null) {
                // No machine
                MachineCreationRequest request = buildMachineCreationRequest(model);
                request.tags.add(tagForInstance);

                machine = cloudHelpers.createInstance(request, OpsSystem.toKey(model), tagForInstance);
            }
        } else {
            if (machine != null) {
                cloudHelpers.terminateMachine(machine);
            }
        }

        getRecursionState().pushChildScope(Machine.class, machine);
    }

    private MachineCreationRequest buildMachineCreationRequest(PersistentInstance model) throws OpsException {
        MachineCreationRequest request = new MachineCreationRequest();
        try {
            request.sshPublicKey = OpenSshUtils.readSshPublicKey(model.sshPublicKey);
        } catch (IOException e) {
            throw new OpsException("Error reading sshPublicKey", e);
        }

        request.cloud = model.cloud;
        request.hostname = model.dnsName;

        request.hostPolicy = model.hostPolicy;

        Tags tags = new Tags();

        request.tags = tags;
        if (model.securityGroup != null) {
            request.securityGroups = Lists.newArrayList();
            request.securityGroups.add(model.securityGroup);
        }

        request.minimumMemoryMB = model.minimumRam;

        request.recipeId = model.recipe;

        request.publicPorts = model.publicPorts;
        return request;
    }

    @Override
    protected void addChildren() throws OpsException {
        final PersistentInstance model = OpsContext.get().getInstance(PersistentInstance.class);

        {
            // Add tag with instance id to persistent instance (very helpful for DNS service!)

            Tagger tagger = injected(Tagger.class);
            tagger.platformLayerKey = OpsSystem.toKey(model);
            tagger.tagChangesProvider = new OpsProvider<TagChanges>() {

                @Override
                public TagChanges get() throws OpsException {
                    Machine machine = OpsContext.get().getInstance(Machine.class);

                    if (machine == null) {
                        if (OpsContext.isDelete())
                            return null;

                        throw new OpsException("No machine in scope");
                    }

                    PlatformLayerKey serverId = machine.getKey();
                    TagChanges changeTags = new TagChanges();
                    changeTags.addTags.add(new Tag(Tag.INSTANCE_KEY, serverId.getUrl()));
                    platformLayer.changeTags(OpsSystem.toKey(model), changeTags);
                    return changeTags;
                }
            };
            addChild(tagger);
        }
    }
}
