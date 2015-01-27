package net.unit8.wscl;

import io.undertow.Undertow;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.websockets.jsr.WebSocketDeploymentInfo;
import org.xnio.OptionMap;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

import javax.servlet.ServletException;
import java.io.IOException;

/**
 * The bootstrap for ClassProvider.
 *
 * @author kawasima
 */
public class ClassProviderStarter {
    public static void main(String[] args) throws IOException, ServletException {
        final Xnio xnio = Xnio.getInstance("nio", Undertow.class.getClassLoader());
        final XnioWorker xnioWorker = xnio.createWorker(OptionMap.builder().getMap());
        final WebSocketDeploymentInfo webSockets = new WebSocketDeploymentInfo()
                .addEndpoint(ClassProvider.class)
                .setWorker(xnioWorker);
        final DeploymentManager deploymentManager = Servlets.defaultContainer()
                .addDeployment(Servlets.deployment()
                        .setClassLoader(ClassProviderStarter.class.getClassLoader())
                        .setContextPath("/")
                        .setDeploymentName("class-provider")
                        .addServletContextAttribute(WebSocketDeploymentInfo.ATTRIBUTE_NAME, webSockets));

        deploymentManager.deploy();
        //noinspection deprecation
        Undertow.builder()
                .addListener(5000, "localhost")
                .setHandler(deploymentManager.start())
                .build()
                .start();
    }
}
