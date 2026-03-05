package com.dripl.merchant.mapper;

import com.dripl.merchant.dto.MerchantResponse;
import com.dripl.merchant.dto.UpdateMerchantInput;
import com.dripl.merchant.entity.Merchant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface MerchantMapper {

    MerchantResponse toDto(Merchant merchant);

    List<MerchantResponse> toDtos(List<Merchant> merchants);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "workspaceId", ignore = true)
    void updateEntity(UpdateMerchantInput dto, @MappingTarget Merchant merchant);
}
