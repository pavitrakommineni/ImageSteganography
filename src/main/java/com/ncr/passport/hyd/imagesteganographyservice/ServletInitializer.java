package com.ncr.passport.hyd.imagesteganographyservice;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

import com.twelvemonkeys.servlet.image.IIOProviderContextListener;

public class ServletInitializer extends SpringBootServletInitializer {
	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
		return application.sources(ImageSteganographyServiceApplication.class);
	}

	@Override
	public void onStartup(ServletContext servletContext) throws ServletException {
		servletContext.addListener(new IIOProviderContextListener());
	}
}
