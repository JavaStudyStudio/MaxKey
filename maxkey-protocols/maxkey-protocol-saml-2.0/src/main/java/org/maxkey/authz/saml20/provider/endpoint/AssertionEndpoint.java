package org.maxkey.authz.saml20.provider.endpoint;

import java.util.ArrayList;
import java.util.HashMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.joda.time.DateTime;
import org.maxkey.authz.saml.common.AuthnRequestInfo;
import org.maxkey.authz.saml.common.EndpointGenerator;
import org.maxkey.authz.saml20.BindingAdapter;
import org.maxkey.authz.saml20.provider.xml.AuthnResponseGenerator;
import org.maxkey.domain.apps.SAML20Details;
import org.maxkey.web.WebContext;
import org.opensaml.saml2.core.Response;
import org.opensaml.saml2.metadata.Endpoint;
import org.opensaml.ws.message.encoder.MessageEncodingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;


@Controller
public class AssertionEndpoint {
	private final static Logger logger = LoggerFactory.getLogger(AssertionEndpoint.class);
	
	private BindingAdapter bindingAdapter;

	@Autowired
	@Qualifier("endpointGenerator")
	EndpointGenerator endpointGenerator;
	
	@Autowired
	@Qualifier("authnResponseGenerator")
	AuthnResponseGenerator authnResponseGenerator;

	@RequestMapping(value = "/authz/saml20/assertion")
	public ModelAndView assertion(HttpServletRequest request,HttpServletResponse response) throws Exception {
		logger.debug("saml20 assertion start.");
		bindingAdapter = (BindingAdapter) request.getSession().getAttribute("samlv20Adapter");
		logger.debug("saml20 assertion get session samlv20Adapter "+bindingAdapter);
		SAML20Details saml20Details = bindingAdapter.getSaml20Details();

		AuthnRequestInfo authnRequestInfo = bindingAdapter.getAuthnRequestInfo();
		
		if (authnRequestInfo == null) {
			logger.warn("Could not find AuthnRequest on the request.  Responding with SC_FORBIDDEN.");
			throw new Exception();
		}

		logger.debug("AuthnRequestInfo: {}", authnRequestInfo);

		ArrayList<GrantedAuthority> grantedAuthority = new ArrayList<GrantedAuthority>();
		grantedAuthority.add(new SimpleGrantedAuthority("ROLE_USER"));

		UsernamePasswordAuthenticationToken authToken = (UsernamePasswordAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();

		for(GrantedAuthority anthGrantedAuthority: authToken.getAuthorities()){
			grantedAuthority.add(anthGrantedAuthority);
		}
		
		String userName =authToken.getPrincipal().toString();
		DateTime authnInstant = new DateTime(request.getSession().getCreationTime());

		String remoteAddress=WebContext.getRequestIpAddress(request);
		HashMap <String,String>attributeMap=new HashMap<String,String>();
		//saml20Details
		Response authResponse = authnResponseGenerator.generateAuthnResponse(
				saml20Details,
				authnRequestInfo,
				userName,
				remoteAddress,
				authnInstant,
				grantedAuthority,
				attributeMap,
				bindingAdapter.getSigningCredential(),
				bindingAdapter.getSpSigningCredential());
		
		Endpoint endpoint = endpointGenerator.generateEndpoint(saml20Details.getSpAcsUrl());

		request.getSession().removeAttribute(AuthnRequestInfo.class.getName());

		// we could use a different adapter to send the response based on
		// request issuer...
		try {
			bindingAdapter.sendSAMLMessage(authResponse, endpoint, request,response);
		} catch (MessageEncodingException mee) {
			logger.error("Exception encoding SAML message", mee);
			throw new Exception(mee);
		}
		return null;
	}

}