/*
 * #%L
 * Alfresco File Transfer Receiver
 * %%
 * Copyright (C) 2005 - 2016 Alfresco Software Limited
 * %%
 * This file is part of the Alfresco software. 
 * If the software was purchased under a paid Alfresco license, the terms of 
 * the paid license agreement will prevail.  Otherwise, the software is 
 * provided under the following open source license terms:
 * 
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
package org.alfresco.repo.transfer.fsr;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.alfresco.repo.descriptor.DescriptorDAO;
import org.alfresco.repo.lock.JobLockService;
import org.alfresco.repo.lock.LockAcquisitionException;
import org.alfresco.repo.transaction.RetryingTransactionHelper;
import org.alfresco.repo.transaction.RetryingTransactionHelper.RetryingTransactionCallback;
import org.alfresco.repo.transfer.ManifestProcessorFactory;
import org.alfresco.repo.transfer.TransferModel;
import org.alfresco.repo.transfer.TransferProgressMonitor;
import org.alfresco.repo.transfer.TransferVersionImpl;
import org.alfresco.repo.transfer.manifest.TransferManifestProcessor;
import org.alfresco.repo.transfer.manifest.XMLTransferManifestReader;
import org.alfresco.repo.transfer.requisite.XMLTransferRequsiteWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.transfer.TransferException;
import org.alfresco.service.cmr.transfer.TransferProgress;
import org.alfresco.service.cmr.transfer.TransferReceiver;
import org.alfresco.service.cmr.transfer.TransferVersion;
import org.alfresco.service.cmr.transfer.TransferProgress.Status;
import org.alfresco.service.descriptor.Descriptor;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.transaction.TransactionService;
import org.alfresco.util.GUID;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.FileCopyUtils;

public class FileTransferReceiver implements TransferReceiver
{
    private final static Log log = LogFactory.getLog(FileTransferReceiver.class);
    private static final String SNAPSHOT_FILE_NAME = "snapshot.xml";

    private static final String MSG_FAILED_TO_CREATE_STAGING_FOLDER = "transfer_service.receiver.failed_to_create_staging_folder";
    private static final String MSG_ERROR_WHILE_STARTING = "transfer_service.receiver.error_start";
    private static final String MSG_TRANSFER_TEMP_FOLDER_NOT_FOUND = "transfer_service.receiver.temp_folder_not_found";
    private static final String MSG_TRANSFER_LOCK_UNAVAILABLE = "transfer_service.receiver.lock_unavailable";
    private static final String MSG_INBOUND_TRANSFER_FOLDER_NOT_FOUND = "transfer_service.receiver.record_folder_not_found";

    private static final String MSG_ERROR_WHILE_ENDING_TRANSFER = "transfer_service.receiver.error_ending_transfer";
    private static final String MSG_ERROR_WHILE_STAGING_SNAPSHOT = "transfer_service.receiver.error_staging_snapshot";
    private static final String MSG_ERROR_WHILE_STAGING_CONTENT = "transfer_service.receiver.error_staging_content";
    private static final String MSG_NO_SNAPSHOT_RECEIVED = "transfer_service.receiver.no_snapshot_received";
    private static final String MSG_ERROR_WHILE_COMMITTING_TRANSFER = "transfer_service.receiver.error_committing_transfer";
    private static final String MSG_ERROR_WHILE_GENERATING_REQUISITE = "transfer_service.receiver.error_generating_requisite";
    private static final String MSG_LOCK_TIMED_OUT = "transfer_service.receiver.lock_timed_out";
    private static final String MSG_LOCK_NOT_FOUND = "transfer_service.receiver.lock_not_found";
    private static final String MSG_TRANSFER_TO_SELF = "transfer_service.receiver.error.transfer_to_self";
    private static final String MSG_INCOMPATIBLE_VERSIONS = "transfer_service.incompatible_versions";
    
    private JobLockService jobLockService;
    /**
     * Reference to the TransactionService instance.
     */
    private TransactionService transactionService;

    /**
     * Locks for the transfers in progress
     * <p>
     * TransferId, Lock
     */
    private Map<String, Lock> locks = new ConcurrentHashMap<String, Lock>();

    /**
     * How many ms before refreshing the lock?
     */
    private long lockRefreshTime = 60000;

    /**
     * How many times to retry to obtain the lock
     */
    private int lockRetryCount = 2;

    /**
     * How long to wait between retries
     */
    private long lockRetryWait = 100;

    /**
     * How long in ms to keep the lock before giving up and ending the transfer, possibly the client has terminated?
     */
    private long lockTimeOut = 20L * 60L * 1000L;  //20 mins default


    private String rootStagingDirectory;

    private String defaultReceivingroot;

    private FileTransferManifestProcessorFactory manifestProcessorFactory;

    private Map<String, File> contents = new ConcurrentHashMap<String, File>();

    private TransferProgressMonitor progressMonitor;

    private FileTransferInfoDAO fileTransferInfoDAO;

    private String fileTransferRootNodeRef;

    private SortedSet<String> setOfNodesBeforeSyncMode;
    
    private DescriptorDAO descriptorDAO;
    private String sourceRepoId;
    
    /**
      * Runnables that will be invoked after commit.
      */
    private List<FSRRunnable> postCommit;
    

    public void cancel(String transferId) throws TransferException
    {
        TransferProgress progress = getProgressMonitor().getProgress(transferId);
        getProgressMonitor().updateStatus(transferId, TransferProgress.Status.CANCELLED);
        if (progress.getStatus().equals(TransferProgress.Status.PRE_COMMIT))
        {
            end(transferId);
        }
    }

    public void commit(String transferId) throws TransferException
    {
        if (log.isDebugEnabled())
        {
            log.debug("Committing transferId=" + transferId);
        }

        /**
         * A side-effect of checking the lock here is that it ensures that the lock timeout is suspended.
         */
        checkLock(transferId);

        final String fTransferId = transferId;

        try
        {
            progressMonitor.updateStatus(transferId, TransferProgress.Status.COMMITTING);

            List<TransferManifestProcessor> commitProcessors = 
                manifestProcessorFactory.getCommitProcessors(FileTransferReceiver.this, fTransferId);

            try
            {
                SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
                SAXParser parser = saxParserFactory.newSAXParser();
                File snapshotFile = getSnapshotFile(fTransferId);

                if (snapshotFile.exists())
                {
                    if (log.isDebugEnabled())
                    {
                        log.debug("Processing manifest file:" + snapshotFile.getAbsolutePath());
                    }
                    // We parse the file as many times as we have processors
                    for (TransferManifestProcessor processor : commitProcessors)
                    {
                        XMLTransferManifestReader reader = new XMLTransferManifestReader(processor);
                        parser.parse(snapshotFile, reader);
                        parser.reset();
                    }
                }
                else
                {
                    progressMonitor.logException(fTransferId,
                            "Unable to start commit. No snapshot file received", new TransferException(
                                    MSG_NO_SNAPSHOT_RECEIVED, new Object[] { fTransferId }));
                }
            }
            catch (Exception ex)
            {
                progressMonitor.logException(transferId, "Caught exception while committing the transfer", ex);
            }

            //Was there an error? If so, change the transfer status to "ERROR" and throw the exception
            Throwable error = progressMonitor.getProgress(transferId).getError();
            if (error != null)
            {
                progressMonitor.updateStatus(transferId, TransferProgress.Status.ERROR);
                if (TransferException.class.isAssignableFrom(error.getClass()))
                {
                    throw (TransferException) error;
                }
                else
                {
                    throw new TransferException(MSG_ERROR_WHILE_COMMITTING_TRANSFER, new Object[] { transferId }, error);
                }
            }

            /**
             * If we get to this point then the commit has taken place without error.
             */
            progressMonitor.updateStatus(transferId, TransferProgress.Status.COMPLETE);
            if (log.isDebugEnabled())
            {
                log.debug("Commit success transferId=" + transferId);
            }
        }
        finally
        {
            /**
             * Clean up at the end of the transfer
             */
            try
            {
                end(transferId);
            }
            catch (Exception ex)
            {
                log.error("Failed to clean up transfer. Lock may still be in place: " + transferId, ex);
            }
            
            // let's run postCommit
            if (postCommit != null && postCommit.size() > 0)
            {
                for (FSRRunnable runnable : postCommit)
                {
                    try
                    {
                        runnable.setTransferId(transferId);
                        runnable.run();
                    }
                    catch (Throwable t)
                    {
                       	log.error("Error from postCommit event t:" + t.toString(), t);
                    }
                }
            } 
        }

    }

    public void commitAsync(final String transferId) throws TransferException
    {
        Lock lock = checkLock(transferId);
        Thread commitThread = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                commit(transferId);
            }
        });
        try
        {
            commitThread.setName("Transfer Commit Thread");
            commitThread.setDaemon(true);
            progressMonitor.updateStatus(transferId, TransferProgress.Status.COMMIT_REQUESTED);
        }
        finally
        {
            lock.enableLockTimeout();
        }
        commitThread.start();
    }

    /*
     * (non-Javadoc)
     *
     * @see org.alfresco.repo.web.scripts.transfer.TransferReceiver#end(org.alfresco.service.cmr.repository.NodeRef)
     */
    public void end(final String transferId)
    {
        if (log.isDebugEnabled())
        {
            log.debug("Request to end transfer " + transferId);
        }
        if (transferId == null)
        {
            throw new IllegalArgumentException("transferId = null");
        }

        try
        {
            Lock lock = locks.get(transferId);
            if (lock != null)
            {
                log.debug("releasing lock:" + lock.lockToken);
                lock.releaseLock();
                locks.remove(lock);
            }

            removeTempFolders(transferId);
        }
        catch (TransferException ex)
        {
            throw ex;
        }
        catch (Exception ex)
        {
            throw new TransferException(MSG_ERROR_WHILE_ENDING_TRANSFER, new Object[] { transferId }, ex);
        }
    }

    public void generateRequsite(String transferId, OutputStream requsiteStream) throws TransferException
    {
        log.debug("Generate Requisite for transfer:" + transferId);
        try
        {
            File snapshotFile = getSnapshotFile(transferId);

            if (snapshotFile.exists())
            {
                log.debug("snapshot does exist");
                SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
                SAXParser parser = saxParserFactory.newSAXParser();
                OutputStreamWriter dest = new OutputStreamWriter(requsiteStream, "UTF-8");

                XMLTransferRequsiteWriter writer = new XMLTransferRequsiteWriter(dest);
                TransferManifestProcessor processor = manifestProcessorFactory.getRequsiteProcessor(
                        FileTransferReceiver.this, transferId, writer);

                XMLTransferManifestReader reader = new XMLTransferManifestReader(processor);

                /**
                 * Now run the parser
                 */
                parser.parse(snapshotFile, reader);

                /**
                 * And flush the destination in case any content remains in the writer.
                 */
                dest.flush();

            }
            log.debug("Generate Requisite done transfer:" + transferId);

        }
        catch (Exception ex)
        {
            if (TransferException.class.isAssignableFrom(ex.getClass()))
            {
                throw (TransferException) ex;
            }
            else
            {
                throw new TransferException(MSG_ERROR_WHILE_GENERATING_REQUISITE, ex);
            }
        }

    }

    public TransferProgressMonitor getProgressMonitor()
    {
        return this.progressMonitor;
    }

    private File getOrCreateFolderIfNotExist(String path)
    {
        File tempFolder = new File(path);
        if (!tempFolder.exists())
        {
            if (!tempFolder.mkdirs())
            {
                tempFolder = null;
                throw new TransferException(MSG_FAILED_TO_CREATE_STAGING_FOLDER);
            }
        }
        return tempFolder;
    }

    /**
     * @param file File
     */
    private void deleteFile(File file)
    {
        if (file.isDirectory())
        {
            File[] fileList = file.listFiles();
            if (fileList != null)
            {
                for (File currentFile : fileList)
                {
                    deleteFile(currentFile);
                }
            }
        }
        file.delete();
    }

    public File getStagingFolder(String transferId)
    {
        if (transferId == null)
        {
            throw new IllegalArgumentException("transferId = " + transferId);
        }
        NodeRef transferNodeRef = new NodeRef(transferId);
        File tempFolder;
        String tempFolderPath = rootStagingDirectory + "/" + transferNodeRef.getId();
        tempFolder = getOrCreateFolderIfNotExist(tempFolderPath);
        return tempFolder;
    }

    public TransferProgress getStatus(String transferId) throws TransferException
    {
        return getProgressMonitor().getProgress(transferId);
    }

    public NodeRef getTempFolder(String transferId)
    {
        if (transferId == null)
        {
            throw new IllegalArgumentException("transferId = " + transferId);
        }

        return new NodeRef(transferId);

    }

    public InputStream getTransferReport(String transferId)
    {
        // TODO Auto-generated method stub
        return null;
    }

    public TransferVersion getVersion()
    {
        Descriptor descriptor = descriptorDAO.getDescriptor();
        TransferVersion version =  new TransferVersionImpl(descriptor.getVersionMajor(), descriptor.getVersionMinor(), 
                descriptor.getVersionRevision(), descriptor.getEdition());
        if (log.isDebugEnabled())
        {
            log.debug("Reporting version number: " + version.toString());
        }
        return version;
    }

    public void prepare(String transferId) throws TransferException
    {
    }

    public void saveContent(String transferId, String contentFileId, InputStream contentStream)
            throws TransferException
    {
        Lock lock = checkLock(transferId);
        try
        {
            File stagedFile = new File(getStagingFolder(transferId), contentFileId);
            if (stagedFile.createNewFile())
            {
                int size = FileCopyUtils.copy(contentStream, new BufferedOutputStream(new FileOutputStream(stagedFile)));
                contents.put(contentFileId, stagedFile);
                progressMonitor.logComment(transferId, "Received content file: " + contentFileId + "; Size = " + size);
            }
        }
        catch (Exception ex)
        {
            throw new TransferException(MSG_ERROR_WHILE_STAGING_CONTENT, 
                    new Object[] { transferId, contentFileId }, ex);
        }
        finally
        {
            lock.enableLockTimeout();
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see org.alfresco.service.cmr.transfer.TransferReceiver#nudgeLock(java.lang.String)
     */
    public Lock checkLock(final String transferId) throws TransferException
    {
        if (transferId == null)
        {
            throw new IllegalArgumentException("checkLock: transferId = null");
        }

        Lock lock = locks.get(transferId);
        if (lock != null)
        {
            if (lock.isActive())
            {
                lock.suspendLockTimeout();
                return lock;
            }
            else
            {
                log.debug("lock not active");
                throw new TransferException(MSG_LOCK_TIMED_OUT, new Object[] { transferId });
            }
        }
        else
        {
            log.debug("lock not found");
            throw new TransferException(MSG_LOCK_NOT_FOUND, new Object[] { transferId });
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see org.alfresco.service.cmr.transfer.TransferReceiver#saveSnapshot(java.io.InputStream)
     */
    public void saveSnapshot(String transferId, InputStream openStream) throws TransferException
    {
        // Check that this transfer still owns the lock
        Lock lock = checkLock(transferId);
        try
        {
            if (log.isDebugEnabled())
            {
                log.debug("Saving snapshot for transferId =" + transferId);
            }

            File snapshotFile = new File(getStagingFolder(transferId), SNAPSHOT_FILE_NAME);
            try
            {
                if (snapshotFile.createNewFile())
                {
                    int size = FileCopyUtils.copy(openStream, new BufferedOutputStream(new FileOutputStream(snapshotFile)));
                    progressMonitor.logComment(transferId, "Received manifest file. Size = " + size);
                    if (log.isDebugEnabled())
                    {
                        log.debug("Saved snapshot for transferId =" + transferId);
                    }
                }
            }
            catch (Exception ex)
            {
                throw new TransferException(MSG_ERROR_WHILE_STAGING_SNAPSHOT, ex);
            }
        }
        finally
        {
            lock.enableLockTimeout();
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see org.alfresco.repo.web.scripts.transfer.TransferReceiver#start()
     */
    public String start(String fromRepositoryId, boolean transferToSelf, TransferVersion fromVersion)
    {
        log.debug("Start transfer");
        sourceRepoId = fromRepositoryId;
        /**
         * Check that transfer is allowed to this repository
         */
        checkTransfer(fromRepositoryId, transferToSelf);

        /**
         * Check that the versions are compatible
         */
        TransferVersion toVersion = getVersion();

        // just check the major version number are equal and if not null
        if (fromVersion.getVersionMajor() == null || toVersion.getVersionMajor() == null
                || !fromVersion.getVersionMajor().equals(toVersion.getVersionMajor()))
        {
            throw new TransferException(MSG_INCOMPATIBLE_VERSIONS, new Object[]
            { "None", fromVersion, toVersion });
        }

        /**
         * First get the transfer lock for this domain
         */

        String lockStr = "transfer.server.default";
        QName lockQName = QName.createQName(TransferModel.TRANSFER_MODEL_1_0_URI, lockStr);
        Lock lock = new Lock(lockQName);

        try
        {

            lock.makeLock();

            /**
             * Transfer Lock held if we get this far
             */
            String transferId = null;

            try
            {
                /**
                 * Now create a transfer record and use its NodeRef as the transfer id
                 */
                final NodeRef relatedTransferRecord = createTransferRecord();
                transferId = relatedTransferRecord.toString();
                getTempFolder(transferId);
                getStagingFolder(transferId);
            }
            catch (Exception e)
            {
                if (log.isDebugEnabled())
                {
                    log.debug("Exception while starting transfer", e);
                    log.debug("releasing lock - we never created the transfer id");
                }
                lock.releaseLock();
                throw new TransferException("Error while starting!", e);
            }

            /**
             * Here if we have begun a transfer and have a valid transfer id
             */
            lock.transferId = transferId;
            locks.put(transferId, lock);
            log.info("transfer started: " + transferId);
            lock.enableLockTimeout();
            progressMonitor.logComment(transferId, "Started transfer");
            progressMonitor.updateStatus(transferId, Status.PRE_COMMIT);
            return transferId;
        }
        catch (LockAcquisitionException lae)
        {
            log.debug("transfer lock is already taken", lae);
            // lock is already taken.
            throw new TransferException(MSG_TRANSFER_LOCK_UNAVAILABLE);
        }

    }

    public void setJobLockService(JobLockService jobLockService)
    {
        this.jobLockService = jobLockService;
    }

    public JobLockService getJobLockService()
    {
        return jobLockService;
    }

    public void setLockRefreshTime(long lockRefreshTime)
    {
        this.lockRefreshTime = lockRefreshTime;
    }

    public long getLockRefreshTime()
    {
        return lockRefreshTime;
    }

    /**
     * A Transfer Lock
     */
    private class Lock implements JobLockService.JobLockRefreshCallback
    {
        /**
         * The name of the lock - unique for each domain
         */
        QName lockQName;

        /**
         * The unique token for this lock instance.
         */
        String lockToken;

        /**
         * The transfer that this lock belongs to.
         */
        String transferId;

        /**
         * Is the lock active ?
         */
        private boolean active = false;

        /**
         * Is the server processing ?
         */
        private boolean processing = false;

        /**
         * When did we last check whether the lock is active
         */
        long lastActive = System.currentTimeMillis();

        public Lock(QName lockQName)
        {
            this.lockQName = lockQName;
        }


        /**
         * Make the lock - called on main thread
         */
        public synchronized void makeLock()
        {
            if(log.isDebugEnabled())
            {
                log.debug("makeLock" + lockQName);
            }

            lockToken = getJobLockService().getLock(lockQName, getLockRefreshTime(), getLockRetryWait(), getLockRetryCount());

            // Got the lock, so mark as active
            active = true;

            if (log.isDebugEnabled())
            {
                log.debug("lock taken: name" + lockQName + " token:" +lockToken);
                log.debug("register lock callback, target lock refresh time :" + getLockRefreshTime());
            }
            getJobLockService().refreshLock(lockToken, lockQName, getLockRefreshTime(), this);
            if (log.isDebugEnabled())
            {
                log.debug("refreshLock callback registered");
            }
        }

        /**
         * If the lock hasn't been released already then this method ensures it can't be.
         * Call this method when processing is currently taking place. After this method is called, 
         * {@link Lock#enableLockTimeout()} must be called once the current processing is complete  
         *
         * Called on main transfer thread as transfer proceeds.
         * @throws TransferException (Lock timeout)
         */
        public synchronized void suspendLockTimeout()
        {
            log.debug("suspend lock called");
            if (active)
            {
                processing = true;
                long now = System.currentTimeMillis();
                // Update lastActive to 1S boundary
                if(now > (lastActive + 1000L))
                {
                    lastActive = now;
                }
            }
            else
            {
                // lock is no longer active
                log.debug("lock not active, throw timed out exception");
                throw new TransferException(MSG_LOCK_TIMED_OUT);
            }
        }

        public synchronized void enableLockTimeout()
        {
            long now = System.currentTimeMillis();
            // Update lastActive to 1S boundary
            if(now > (lastActive + 1000L))
            {
                lastActive = now;
                log.debug("start waiting : lastActive:" + lastActive);
            }
            processing = false;
        }

        /**
         * Release the lock
         *
         * Called on main thread
         */
        public synchronized void releaseLock()
        {
            if(log.isDebugEnabled())
            {
                log.debug("transfer service about to releaseLock : " + lockQName);
            }

            if (active)
            {
                active = false;
                getJobLockService().releaseLock(lockToken, lockQName);
            }
        }

        /**
         * Called by Job Lock Service to determine whether the lock is still active
         */
        @Override
        public synchronized boolean isActive()
        {
            long now = System.currentTimeMillis();
            if(active)
            {
                if(!processing)
                {
                    if(now > (lastActive + getLockTimeOut()))
                    {
                        return false;
                    }
                }
            }

            if(log.isDebugEnabled())
            {
                log.debug("transfer service callback isActive: " + active);
            }

            return active;
        }

        /**
         * Called by Job Lock Service on release of the lock after time-out
         */
        @Override
        public synchronized void lockReleased()
        {
            if(active)
            {
                active = false;
                log.info("transfer service: lock has timed out, timeout :" + lockQName);
                timeout(transferId);
            }
        }
    }

    /**
     * Timeout a transfer. Called after the lock has been released via a timeout. This is the last chance to clean up.
     *
     * @param transferId String
     */
    private void timeout(final String transferId)
    {
        log.info("Inbound Transfer has timed out transferId:" + transferId);
        /*
         * There is no transaction or authentication context in this method since it is called via a timer thread.
         */
        final RetryingTransactionCallback<Object> timeoutCB = new RetryingTransactionCallback<Object>()
            {

                public Object execute() throws Throwable
                {
                    TransferProgress progress = getProgressMonitor().getProgress(transferId);

                    if (progress.getStatus().equals(TransferProgress.Status.PRE_COMMIT))
                    {
                        log.warn("Inbound Transfer Lock Timeout - transferId:" + transferId);
                        /**
                         * Did not get out of PRE_COMMIT. The client has probably "gone away" after calling "start", but
                         * before calling commit, cancel or error.
                         */
                        locks.remove(transferId);
                        removeTempFolders(transferId);
                        Object[] msgParams =
                        { transferId };
                        getProgressMonitor().logException(transferId, "transfer timeout",
                                new TransferException("Lock time out", msgParams));
                        getProgressMonitor().updateStatus(transferId, TransferProgress.Status.ERROR);
                    }
                    else
                    {
                        // We got beyond PRE_COMMIT, therefore leave the clean up to either
                        // commit, cancel or error command, since there may still be "in-flight"
                        // transfer in another thread. Although why, in that case, are we here?
                        log.warn("Inbound Transfer Lock Timeout - already past PRE-COMMIT - do no cleanup transferId:"
                                + transferId);
                    }
                    return null;
                }
            };

        transactionService.getRetryingTransactionHelper().doInTransaction(timeoutCB, false, true);
    }

    private void removeTempFolders(final String transferId)
    {
        NodeRef tempStoreNode = null;
        try
        {
            log.debug("Deleting temporary store node...");
            tempStoreNode = getTempFolder(transferId);
            log.debug("Deleted temporary store node.");
        }
        catch (Exception ex)
        {
            log.warn("Failed to delete temp store node for transfer id " + transferId + "\nTemp store noderef = "
                    + tempStoreNode);
        }

        File stagingFolder = null;
        try
        {
            log.debug("delete staging folder " + transferId);
            // Delete the staging folder.
            stagingFolder = getStagingFolder(transferId);
            deleteFile(stagingFolder);
            log.debug("Staging folder deleted");
        }
        catch (Exception ex)
        {
            log.warn("Failed to delete staging folder for transfer id " + transferId + "\nStaging folder = "
                    + stagingFolder.toString());
        }
    }

    public boolean isContentNewOrModified(final String nodeRef, final String contentUrl)
    {
        RetryingTransactionHelper txHelper = transactionService.getRetryingTransactionHelper();

        return txHelper.doInTransaction(new RetryingTransactionHelper.RetryingTransactionCallback<Boolean>()
        {
            public Boolean execute() throws Throwable
            {
                if (log.isDebugEnabled())
                {
                    log.debug("Checking content for node " + nodeRef);
                    log.debug("Supplied content URL is " + contentUrl);
                }
                boolean result = false;
                FileTransferInfoEntity fileTransferInfoEntity = fileTransferInfoDAO
                        .findFileTransferInfoByNodeRef(nodeRef);
                if (fileTransferInfoEntity == null)
                {
                    result = true;
                    if (log.isDebugEnabled())
                    {
                        log.debug("No record found for this node");
                    }
                }
                else if (contentUrl != null && !contentUrl.equals(fileTransferInfoEntity.getContentUrl()))
                {
                    result = true;
                    if (log.isDebugEnabled())
                    {
                        log.debug("Supplied content URL is different to the one on record: " + 
                                fileTransferInfoEntity.getContentUrl());
                    }
                }
                else
                {
                    if (log.isDebugEnabled())
                    {
                        log.debug("Content URL has not changed");
                    }
                }
                return result;
            }
        }, true, false);
    }

    protected File getSnapshotFile(String transferId)
    {
        return new File(getStagingFolder(transferId), SNAPSHOT_FILE_NAME);
    }

    /**
     * Check Whether transfer is allowed from the specified repository. Called prior to "begin".
     */

    private void checkTransfer(String fromRepository, boolean transferToSelf)
    {
        // to be filled
    }

    /**
     * @return NodeRef
     */
    private NodeRef createTransferRecord()
    {
        return new NodeRef("workspace://SpaceStore/" + GUID.generate());
    }

    public long getLockRetryWait()
    {
        return lockRetryWait;
    }

    public void setLockRetryWait(long lockRetryWait)
    {
        this.lockRetryWait = lockRetryWait;
    }

    public int getLockRetryCount()
    {
        return lockRetryCount;
    }

    public void setLockRetryCount(int lockRetryCount)
    {
        this.lockRetryCount = lockRetryCount;
    }

    public long getLockTimeOut()
    {
        return lockTimeOut;
    }

    public void setLockTimeOut(long lockTimeOut)
    {
        this.lockTimeOut = lockTimeOut;
    }


    public void setTransactionService(TransactionService transactionService)
    {
        this.transactionService = transactionService;
    }

    public String getRootStagingDirectory()
    {
        return rootStagingDirectory;
    }

    public void setRootStagingDirectory(String rootStagingDirectory)
    {
        this.rootStagingDirectory = rootStagingDirectory;
    }

    public ManifestProcessorFactory getManifestProcessorFactory()
    {
        return manifestProcessorFactory;
    }

    public void setManifestProcessorFactory(ManifestProcessorFactory manifestProcessorFactory)
    {
        this.manifestProcessorFactory = (FileTransferManifestProcessorFactory) manifestProcessorFactory;
    }

    public void setProgressMonitor(TransferProgressMonitor progressMonitor)
    {
        this.progressMonitor = progressMonitor;
    }

    public String getDefaultReceivingroot()
    {
        return defaultReceivingroot;
    }

    public void setDefaultReceivingroot(String defaultReceivingroot)
    {
        this.defaultReceivingroot = defaultReceivingroot;
    }

    public Map<String, File> getContents()
    {
        return contents;
    }

    public void setTransferRootNode(String rootFileSystem)
    {
        this.fileTransferRootNodeRef = rootFileSystem;
    }

    public String getTransferRootNode()
    {
        return this.fileTransferRootNodeRef;
    }

    public void setFileTransferInfoDAO(FileTransferInfoDAO fileTransferInfoDAO)
    {
        this.fileTransferInfoDAO = fileTransferInfoDAO;
    }

    public void setDescriptorDAO(DescriptorDAO descriptorDAO)
    {
        this.descriptorDAO = descriptorDAO;
    }

    public DbHelper getDbHelper()
    {
        return new DbHelperImpl(fileTransferInfoDAO, transactionService, sourceRepoId);
    }
        
   	public void setPostCommit(List<FSRRunnable> postCommit) {
   		this.postCommit = postCommit;
   	}
   
   	public List<FSRRunnable> getPostCommit() {
   		return postCommit;
   	}
}
