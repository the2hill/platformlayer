package org.platformlayer.ops.filesystem;

import java.io.File;

import org.apache.log4j.Logger;
import org.platformlayer.ops.Handler;
import org.platformlayer.ops.OperationType;
import org.platformlayer.ops.OpsContext;
import org.platformlayer.ops.OpsTarget;

import com.google.common.base.Objects;

//@Icon("folder")
public class ManagedSymlink extends ManagedFilesystemItem {
    static final Logger log = Logger.getLogger(ManagedSymlink.class);

    public File symlinkTarget;

    public ManagedSymlink() {
        // Default mode is meaningless on a symlink
        setFileMode(null);
    }

    public static ManagedSymlink build(File alias, File target) {
        ManagedSymlink link = OpsContext.get().getInjector().getInstance(ManagedSymlink.class);
        link.filePath = alias;
        link.symlinkTarget = target;
        return link;
    }

    @Handler
    public void doConfigureValidate(OperationType operationType, OpsTarget target) throws Exception {
        File filePath = getFilePath();
        FilesystemInfo fsInfo = target.getFilesystemInfoFile(filePath);
        boolean exists = (fsInfo != null);

        File symlinkTarget = getSymlinkTarget();

        boolean symlinkTargetMatch = false;
        if (fsInfo != null) {
            symlinkTargetMatch = Objects.equal(fsInfo.symlinkTarget, symlinkTarget.toString());
        }

        if (operationType.isConfigure()) {
            if (!exists) {
                target.symlink(symlinkTarget, filePath, false);
                doUpdateAction(target);
            } else {
                if (!symlinkTargetMatch) {
                    target.symlink(symlinkTarget, filePath, true);
                }
            }

            configureOwnerAndMode(target, fsInfo);

            // FilesystemInfo targetInfo = agent.getFilesystemInfoFile(getSymlinkTarget());
            // configureOwnerAndMode(agent, targetInfo, getSymlinkTarget());
        }

        if (operationType.isValidate()) {
            if (!exists) {
                OpsContext.get().addWarning(this, "DoesNotExist", "Symlink not found: " + filePath);
            } else if (!symlinkTargetMatch) {
                OpsContext.get().addWarning(this, "TargetMismatch", "Symlink points at wrong target: " + fsInfo);

                validateOwner(fsInfo);

                // Mode is meaningless on symlinks
                // validateMode(fsInfo);
            }
        }

        if (operationType.isDelete()) {
            if (exists) {
                target.rm(filePath);
                doUpdateAction(target);
            }
        }
    }

    protected File getSymlinkTarget() {
        return symlinkTarget;
    }

}
