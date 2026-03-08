package com.balanced.aggregation.mapper;

import com.balanced.aggregation.dto.BankConnectionResponse;
import com.balanced.aggregation.entity.BankConnection;
import org.mapstruct.Mapper;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface BankConnectionMapper {

    BankConnectionResponse toDto(BankConnection entity);

    List<BankConnectionResponse> toDtos(List<BankConnection> entities);
}
