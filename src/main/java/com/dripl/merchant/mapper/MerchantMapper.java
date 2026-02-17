package com.dripl.merchant.mapper;

import com.dripl.merchant.dto.MerchantDto;
import com.dripl.merchant.dto.UpdateMerchantDto;
import com.dripl.merchant.entity.Merchant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface MerchantMapper {

    MerchantDto toDto(Merchant merchant);

    List<MerchantDto> toDtos(List<Merchant> merchants);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "workspaceId", ignore = true)
    void updateEntity(UpdateMerchantDto dto, @MappingTarget Merchant merchant);
}
