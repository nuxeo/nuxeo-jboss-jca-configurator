/*
 * (C) Copyright 2008 Nuxeo SAS (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Thierry Delprat
 */

package org.nuxeo.jboss.jca;

import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.resource.connectionmanager.JBossManagedConnectionPoolMBean;
import org.nuxeo.ecm.core.api.repository.Repository;
import org.nuxeo.ecm.core.api.repository.RepositoryManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.management.ServerLocator;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;

/**
 * Simple Frameworklistener that configure JCA connector for Nuxeo Repositories at startup time
 * This is useful when using Static EAR deployment since JBoss property service fails managing non string properties on JCA descriptor
 *
 * @author Thierry Delprat
 */
public class JBossJCADynamicConfigurator extends DefaultComponent implements  FrameworkListener {

    protected static Log log = LogFactory.getLog(JBossJCADynamicConfigurator.class);

    public static final String PREFIX = "org.nuxeo.jca.config.";
    public static final String MIN_POOL = ".min-pool-size";
    public static final String MAX_POOL = ".max-pool-size";
    public static final String TIMEOUT = ".timeout";
    public static final String IDLE = ".idle";
    public static final String VALID = ".validation";
    public static final String DOMAIN = "jboss.jca";

    @Override
    public void activate(ComponentContext context) throws Exception {
        Bundle host = context.getRuntimeContext().getBundle();
        BundleContext ctx = host.getBundleContext();
        if (ctx == null) {
            return;
        }
        ctx.addFrameworkListener(this);
    }

    @Override
    public void deactivate(ComponentContext context) throws Exception {
        Bundle host = context.getRuntimeContext().getBundle();
        BundleContext ctx = host.getBundleContext();
        if (ctx == null) {
            return;
        }
        ctx.removeFrameworkListener(this);
    }


    protected void configureRepository(String repoName) throws Exception {

        ServerLocator locator = Framework.getLocalService(ServerLocator.class);

        MBeanServer server = locator.lookupServer(DOMAIN);

        ObjectName name = new ObjectName(DOMAIN + ":name=NXRepository/" + repoName + ",service=ManagedConnectionPool");

        JBossManagedConnectionPoolMBean jca = JMX.newMBeanProxy(server, name, JBossManagedConnectionPoolMBean.class);

        String min = Framework.getProperty(PREFIX + repoName + MIN_POOL);
        String max = Framework.getProperty(PREFIX + repoName + MAX_POOL);
        String timeout = Framework.getProperty(PREFIX + repoName + TIMEOUT);
        String idle = Framework.getProperty(PREFIX + repoName + IDLE);
        String valid = Framework.getProperty(PREFIX + repoName + VALID);

        if (min!=null) {
            jca.setMinSize(Integer.parseInt(min));
        }
        if (max!=null) {
            jca.setMaxSize(Integer.parseInt(max));
        }
        if (timeout!=null) {
            jca.setBlockingTimeoutMillis(Integer.parseInt(timeout));
        }
        if (idle!=null) {
            jca.setIdleTimeoutMinutes(Integer.parseInt(idle));
        }
        if (valid!=null) {
            jca.setBackGroundValidationMillis(Integer.parseInt(valid));
        }

        // restart pool so that configuration is taken into account
        jca.stop();
        jca.start();

    }

    protected void configureRepositories() throws Exception {
        RepositoryManager rm = Framework.getLocalService(RepositoryManager.class);
        if (rm!=null) {
            for (Repository repo : rm.getRepositories()) {
                log.info("Check JCA configuration for repository " + repo.getName());
                configureRepository(repo.getName());
            }
        }
    }

    @Override
    public void frameworkEvent(FrameworkEvent event) {
        if (event.getType() == FrameworkEvent.STARTED) {
            ClassLoader jbossCL = Thread.currentThread().getContextClassLoader();
            ClassLoader nuxeoCL = JBossJCADynamicConfigurator.class.getClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(nuxeoCL);
                log.info("JBoss Dynamic JCA pool configuration");
                    try {
                        configureRepositories();
                    } catch (Exception e) {
                        log.error("Error during JCA pool init",e);
                    }
            } finally {
                Thread.currentThread().setContextClassLoader(jbossCL);
                log.debug("JBoss ClassLoader restored");
            }
        }
    }

}
