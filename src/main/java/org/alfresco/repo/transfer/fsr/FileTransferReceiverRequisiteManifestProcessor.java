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

import java.io.Serializable;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.transfer.AbstractManifestProcessorBase;
import org.alfresco.repo.transfer.TransferCommons;
import org.alfresco.repo.transfer.manifest.TransferManifestDeletedNode;
import org.alfresco.repo.transfer.manifest.TransferManifestHeader;
import org.alfresco.repo.transfer.manifest.TransferManifestNormalNode;
import org.alfresco.repo.transfer.requisite.TransferRequsiteWriter;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.transfer.TransferReceiver;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @author Brian Remmington
 * @author Philippe Dubois
 * @author Mark Rogers
 */
public class FileTransferReceiverRequisiteManifestProcessor extends AbstractManifestProcessorBase
{
    private TransferRequsiteWriter out;
    private FileTransferReceiver fileTransferReceiver;

    private static final Log log = LogFactory.getLog(FileTransferReceiverRequisiteManifestProcessor.class);

    /**
     * @param receiver TransferReceiver
     * @param transferId String
     * @param out TransferRequsiteWriter
     */
    public FileTransferReceiverRequisiteManifestProcessor(
            TransferReceiver receiver,
            String transferId,
            TransferRequsiteWriter out)
    {
        super(receiver, transferId);
        this.out = out;
        fileTransferReceiver = (FileTransferReceiver) receiver;
    }

    protected void endManifest()
    {
        log.debug("End Requisite");
        out.endTransferRequsite();
    }

    protected void processNode(TransferManifestDeletedNode node)
    {
        // NOOP
    }

    protected void processNode(TransferManifestNormalNode node)
    {

        //Skip over any nodes that are not parented with a cm:contains association or 
        //are not content nodes (we don't need their content)
        if (!ContentModel.ASSOC_CONTAINS.equals(node.getPrimaryParentAssoc().getTypeQName()) ||
                !ContentModel.TYPE_CONTENT.equals(node.getAncestorType()))
        {
            return;
        }

        Serializable value = node.getProperties().get(ContentModel.PROP_CONTENT);
        if ((value != null) && ContentData.class.isAssignableFrom(value.getClass()))
        {
            ContentData srcContent = (ContentData) value;
            if (srcContent.getContentUrl() != null && !srcContent.getContentUrl().isEmpty())
            {
                // Only ask for content if content is new or if contentUrl is modified
                boolean contentisMissing = fileTransferReceiver.isContentNewOrModified(
                        node.getNodeRef().toString(), srcContent.getContentUrl());
                if (contentisMissing)
                {
                    if (log.isDebugEnabled())
                    {
                        log.debug("No node on destination, content is required: " + srcContent.getContentUrl());
                    }
                    out.missingContent(node.getNodeRef(), ContentModel.PROP_CONTENT, 
                            TransferCommons.URLToPartName(srcContent.getContentUrl()));
                }
            }
        }
    }

    protected void processHeader(TransferManifestHeader header)
    {
    }

    /*
     * (non-Javadoc)
     *
     * @see org.alfresco.repo.transfer.manifest.TransferManifestProcessor#startTransferManifest()
     */
    protected void startManifest()
    {
        log.debug("Start Requisite");
        out.startTransferRequsite();
    }

    /**
     * @param nodeService the nodeService to set
     */
    public void setNodeService(NodeService nodeService)
    {

    }

}
