/*
 * #%L
 * Alfresco File Transfer Receiver Distribution
 * %%
 * Copyright (C) 2005 - 2017 Alfresco Software Limited
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

import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.connector.Connector;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.coyote.http11.Http11Protocol;

/**
 * HTTP Connector
 *
 */
public class Http11Connector extends Connector
{
    private static final Log log = LogFactory.getLog(Http11Connector.class);

    public Http11Connector()
    {
        super("HTTP/1.1");

        if (log.isDebugEnabled())
        {
            addLifecycleListener(new LifecycleListener()
            {
                public void lifecycleEvent(LifecycleEvent event)
                {
                    if (log.isDebugEnabled())
                    {
                        log.debug("event: " + event.getType());
                    }
                }
            });
        }
    }
    
    protected Http11Protocol getHttp11Protocol()
    {
        return (Http11Protocol) getProtocolHandler();
    }
    
    public void setSSLEnabled(boolean value)
    {
        this.getHttp11Protocol().setSSLEnabled(value);
    }

}
