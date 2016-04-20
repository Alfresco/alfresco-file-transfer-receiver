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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.ibatis.SerializableTypeHandler;
import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.springframework.context.ApplicationEvent;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.extensions.surf.util.AbstractLifecycleBean;


public class SchemaBootstrap extends AbstractLifecycleBean
{
    private static Log log = LogFactory.getLog(SchemaBootstrap.class);
    private ResourcePatternResolver rpr = new PathMatchingResourcePatternResolver(this.getClass().getClassLoader());
    private BasicDataSource dataSource;
    // creation script URL
    private String creationScript;

    public void init()
    {
        // empty
        SerializableTypeHandler.setSerializableType(Types.BLOB);
    }

    // @Override
    protected void onBootstrap(ApplicationEvent arg0)
    {
        Connection con = null;

        // create DB if necessary this is done by the jDBC driver configured with option
        // create=true
        try
        {
            // create DB if necessary this is done by the jDBC driver configured with option
            // create=true. If DB exist it does not recreate it.
            con = dataSource.getConnection();
            con.close();
            con = null;
            createDBTables();
        }
        catch (SQLException e)
        {
            if (log.isDebugEnabled())
            {
                log.debug("Error in DB creation or connection:" + e.getMessage());
            }

        }
        catch (Exception e)
        {
            if (log.isDebugEnabled())
            {
                log.debug("Error in DB creation:" + e.getMessage());
            }
        }
        finally
        {
            try
            {
                if (con != null)
                    con.close();
            }
            catch (SQLException e)
            {
                if (log.isDebugEnabled())
                {
                    log.debug("Error in closing connection:" + e.getMessage());
                }
            }
        }

    }

    /**
     * Execute database creation script.
     */
    protected void createDBTables()
    {

        // check if tables in DB exist?
        if (isDBInitialized())
            return;
        Resource resourceScript = rpr.getResource(creationScript);
        if (!resourceScript.exists())
        {
            if (log.isDebugEnabled())
            {
                log.debug("Ressource " + creationScript + " does not exist!");
            }
            // throw exception
            throw new AlfrescoRuntimeException("Creation script " + creationScript + " not found!");
        }

        // execute script
        executeSript(resourceScript);

    }

    protected boolean isDBInitialized()
    {
        Connection con = null;
        Statement st = null;
        try
        {
            // check if table "" exist. If exist, consider DB tables already there.
            con = dataSource.getConnection();
            // We create a table...
            st = con.createStatement();
            st.execute("select count(*) from version");
            return true;
        }
        catch (Exception e)
        {
            if (log.isDebugEnabled())
            {
                log.debug("Error in isDBInitialized:" + e.getMessage());
            }
        }
        finally
        {
            if (st != null)
            {
                try
                {
                    st.close();
                }
                catch (Exception e)
                {
                }
            }
            if (con != null)
            {
                try
                {
                    con.close();
                }
                catch (Exception e)
                {
                }
            }
        }
        return false;
    }

    protected void executeSript(Resource resourceScript)
    {
        BufferedReader reader = null;
        try
        {
            // run the creation script with autocommit true and stop on error.
            ScriptRunner runner = new ScriptRunner(dataSource.getConnection());
            runner.setLogWriter(null);
            runner.setAutoCommit(true);
            runner.setStopOnError(true);
            reader = new BufferedReader(new InputStreamReader(resourceScript.getInputStream(), "UTF-8"));
            runner.runScript(reader);
        }
        catch (SQLException sql)
        {
            throw new AlfrescoRuntimeException("Creation script " + creationScript + " failed!", sql);
        }
        catch (IOException io)
        {
            if (reader != null)
            {
                try
                {
                    reader.close();
                }
                catch (Exception e)
                {
                }
            }
            throw new AlfrescoRuntimeException("Creation script " + creationScript + " could not optain reader!!");
        }
    }

    @Override
    protected void onShutdown(ApplicationEvent arg0)
    {
        if (log.isDebugEnabled())
        {
            log.warn("FTR shutting down");
        }
    }

    public void setDataSource(BasicDataSource dataSource)
    {
        this.dataSource = dataSource;
    }

    public void setCreationScript(String creationScript)
    {
        this.creationScript = creationScript;
    }

}
