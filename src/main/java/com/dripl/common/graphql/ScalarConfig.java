package com.dripl.common.graphql;

import graphql.language.StringValue;
import graphql.scalars.ExtendedScalars;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import graphql.schema.idl.RuntimeWiring;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Configuration
public class ScalarConfig {

    private static final GraphQLScalarType LOCAL_DATE_TIME = GraphQLScalarType.newScalar()
            .name("LocalDateTime")
            .description("Java LocalDateTime as ISO-8601 string")
            .coercing(new Coercing<LocalDateTime, String>() {
                @Override
                public String serialize(Object input) throws CoercingSerializeException {
                    if (input instanceof LocalDateTime ldt) {
                        return ldt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    }
                    throw new CoercingSerializeException("Expected LocalDateTime but got " + input.getClass().getSimpleName());
                }

                @Override
                public LocalDateTime parseValue(Object input) throws CoercingParseValueException {
                    if (input instanceof String s) {
                        return LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    }
                    throw new CoercingParseValueException("Expected String but got " + input.getClass().getSimpleName());
                }

                @Override
                public LocalDateTime parseLiteral(Object input) throws CoercingParseLiteralException {
                    if (input instanceof StringValue sv) {
                        return LocalDateTime.parse(sv.getValue(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    }
                    throw new CoercingParseLiteralException("Expected StringValue but got " + input.getClass().getSimpleName());
                }
            })
            .build();

    @Bean
    public RuntimeWiringConfigurer runtimeWiringConfigurer() {
        return wiringBuilder -> wiringBuilder
                .scalar(ExtendedScalars.GraphQLBigDecimal)
                .scalar(LOCAL_DATE_TIME);
    }
}
