package org.jivesoftware.openfire.plugin.gojara.base;

import java.io.File;
//import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
//import java.util.Map;
import java.util.Set;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
//import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.component.ComponentEventListener;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.plugin.gojara.messagefilter.MainInterceptor;
//import org.jivesoftware.openfire.plugin.gojara.messagefilter.handler.AbstractInterceptorHandler;
//import org.jivesoftware.openfire.plugin.gojara.messagefilter.handler.GatewayInterceptorHandler;
import org.jivesoftware.openfire.plugin.gojara.utils.XpathHelper;
//import org.jivesoftware.openfire.session.ComponentSession;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.jivesoftware.util.PropertyEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;

/**
 * @author Holger Bergunde
 * 
 *         This class is the basic reprasentation for the GoJara plugin. It is
 *         the entry point for openfire to start or stop this plugin.
 * 
 *         GoJara has been developed to support XEP-xxx Remote Roster
 *         Management. Further information: <a
 *         href="http://jkaluza.fedorapeople.org/remote-roster.html">Here</a>
 * 
 *         RemoteRoster enables Spectrum IM support for Openfire. Currently only
 *         2.3, 2.4 and 2.5 implemented. 2.1 and 2.2 of the protocol standard is
 *         not supported by Spectrum IM
 */
public class RemoteRosterPlugin implements Plugin {

	private static final Logger Log = LoggerFactory.getLogger(RemoteRosterPlugin.class);
	private static PluginManager pluginManager;
//	private final SessionManager _sessionManager = SessionManager.getInstance();
//	private Map<String, AbstractInterceptorHandler> _interceptors = new HashMap<String, AbstractInterceptorHandler>();
	private Set<String> _waitingForIQResponse = new HashSet<String>();
	private PropertyEventListener _settingsObserver;
	private MainInterceptor mainInterceptor = new MainInterceptor();
	private InterceptorManager iManager = InterceptorManager.getInstance();
	
	
	public void initializePlugin(PluginManager manager, File pluginDirectory) {
		Log.debug("Starting RemoteRoster Plugin");
		pluginManager = manager;
		iManager.addInterceptor(mainInterceptor);
		manageExternalComponents();
		listenToSettings();
		Log.debug("Started Gojara successfully. Currently running interceptors: "+iManager.getInterceptors().size());
		
	}

	/*
	 * Handles external components that connect to openfire. We check if the
	 * external component is maybe a gateway and interesting for us
	 */
	private void manageExternalComponents() {
		InternalComponentManager compManager = InternalComponentManager.getInstance();
		compManager.addListener(new ComponentEventListener() {
			/*
			 * Check if the unregistered component contains to one of our
			 * package interceptors
			 */
			
			public void componentUnregistered(JID componentJID) {
//				ComponentSession session = _sessionManager.getComponentSession(componentJID.getDomain());
//				if (session != null && _interceptors.containsKey(session.getExternalComponent().getInitialSubdomain())) {
//					String initialSubdomain = session.getExternalComponent().getInitialSubdomain();
					// Remove it from Map & ComponentManager
					mainInterceptor.removeTransport(componentJID.toString());
//				}
			}

			/*
			 * If there is a new external Component, check if it is a gateway
			 * and add create a package interceptor if it is enabled
			 */
			
			public void componentRegistered(JID componentJID) {
				_waitingForIQResponse.add(componentJID.getDomain());
			}

			
			public void componentInfoReceived(IQ iq) {
				String from = iq.getFrom().getDomain();
				// Waiting for this external component sending an IQ response to
				// us?
				if (_waitingForIQResponse.contains(from)) {
					Element packet = iq.getChildElement();
					Document doc = packet.getDocument();
					List<Node> nodes = XpathHelper.findNodesInDocument(doc, "//disco:identity[@category='gateway']");
					// Is this external component a gateway and there is no
					// package interceptor for it?
//					if (nodes.size() > 0 && !_interceptors.containsKey(from)) {
					if (nodes.size() > 0) {
						updateInterceptors(from);
					}

					// We got the IQ, we can now remove it from the set, because
					// we are not waiting any more
					_waitingForIQResponse.remove(from);
				}
			}
		});
	}

	/*
	 * Registers a listener for JiveGlobals. We might restart our service, if
	 * there were some changes for our gateways
	 */
	private void listenToSettings() {
		_settingsObserver = new RemoteRosterPropertyListener() {
			@Override
			protected void changedProperty(String prop) {
				updateInterceptors(prop);
			}
		};
		PropertyEventDispatcher.addListener(_settingsObserver);
	}

	public void destroyPlugin() {
//		for (String key : _interceptors.keySet()) {
//			_interceptors.get(key).stop();
//		}
		mainInterceptor.freeze();
		iManager.removeInterceptor(mainInterceptor);
		PropertyEventDispatcher.removeListener(_settingsObserver);
		pluginManager = null;
		mainInterceptor = null;
	}

	private void updateInterceptors(String componentJID) {
		boolean allowed = JiveGlobals.getBooleanProperty("plugin.remoteroster.jids." + componentJID, false);
		if (allowed) {
//			if (!_interceptors.containsKey(componentJID)) {
//				createNewPackageIntercetor(componentJID);
//			}
			mainInterceptor.addTransport(componentJID);
		} else {
//			if (_interceptors.containsKey(componentJID)) {
//				removeInterceptor(componentJID);
//			}
			mainInterceptor.removeTransport(componentJID);
		}
	}

	public String getName() {
		return "gojara";

	}

	public static PluginManager getPluginManager() {
		return pluginManager;
	}

//	private void removeInterceptor(String initialSubdomain) {
//		mainInterceptor.removeTransport(initialSubdomain);
////		AbstractInterceptorHandler interceptor = _interceptors.get(initialSubdomain);
////		if (interceptor != null) {
////			_interceptors.remove(initialSubdomain);
////			interceptor.stop();
////		}
//	}
//
//	/*
//	 * We are handling all our gateways in a Map with subdomain, like
//	 * icq.myserver.com, to ensure that there is only one interceptor for a
//	 * specified gateway
//	 */
//	private void createNewPackageIntercetor(String initialSubdomain) {
//		mainInterceptor.addTransport(initialSubdomain);
////		AbstractInterceptorHandler interceptor = new GatewayInterceptorHandler(initialSubdomain);
////		_interceptors.put(initialSubdomain, interceptor);
////		interceptor.start();
//	}

}