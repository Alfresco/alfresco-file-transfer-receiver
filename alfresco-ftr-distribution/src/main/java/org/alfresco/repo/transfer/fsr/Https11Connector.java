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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * HTTPS Connector
 *
 */
public class Https11Connector extends Http11Connector
{
    private static final Log log = LogFactory.getLog(Https11Connector.class);

    public Https11Connector()
    {
        if (log.isDebugEnabled())
        {
            log.debug("init");
        }
    }

    public void setAlgorithm(String value)
    {
        this.getHttp11Protocol().setAlgorithm(value);
    }

    public void setClientAuth(String value)
    {
        this.getHttp11Protocol().setClientAuth(value);
    }

    public void setCrlFile(String value)
    {
        this.getHttp11Protocol().setCrlFile(value);
    }

    public void setKeyAlias(String value)
    {
        this.getHttp11Protocol().setKeyAlias(value);
    }

    public void setKeyPass(String value)
    {
        this.getHttp11Protocol().setKeyPass(value);
    }

    public void setKeystoreFile(String value)
    {
        this.getHttp11Protocol().setKeystoreFile(value);
    }

    public void setKeystorePass(String value)
    {
        this.getHttp11Protocol().setKeystorePass(value);
    }

    public void setKeystoreProvider(String value)
    {
        this.getHttp11Protocol().setKeystoreProvider(value);
    }

    public void setKeystoreType(String value)
    {
        this.getHttp11Protocol().setKeystoreType(value);
    }

    public void setSessionTimeout(String value)
    {
        this.getHttp11Protocol().setSessionTimeout(value);
    }

    public void setSessionCacheSize(String value)
    {
        this.getHttp11Protocol().setSessionCacheSize(value);
    }

    public void setSslProtocol(String value)
    {
        this.getHttp11Protocol().setSslProtocol(value);
    }

    public void setSslImplementationName(String value)
    {
        this.getHttp11Protocol().setSslImplementationName(value);
    }

    public void setTruststoreAlgorithm(String value)
    {
        this.getHttp11Protocol().setTruststoreAlgorithm(value);
    }

    public void setTruststoreFile(String value)
    {
        this.getHttp11Protocol().setTruststoreFile(value);
    }

    public void setTruststorePass(String value)
    {
        this.getHttp11Protocol().setTruststorePass(value);
    }

    public void setTruststoreProvider(String value)
    {
        this.getHttp11Protocol().setTruststoreProvider(value);
    }

    public void setTruststoreType(String value)
    {
        this.getHttp11Protocol().setTruststoreType(value);
    }

}
