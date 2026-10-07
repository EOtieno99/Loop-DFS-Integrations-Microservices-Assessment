package com.example.loop_assessment.config;

import com.example.loop_assessment.soap_resources.SoapProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SoapProperties.class)
public class ApplicationConfiguration {}
