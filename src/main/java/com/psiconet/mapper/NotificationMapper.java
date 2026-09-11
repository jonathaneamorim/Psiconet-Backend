package com.psiconet.mapper;

import com.psiconet.model.dtos.NotificationDTO;
import com.psiconet.model.entities.Notification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    @Mapping(target = "isRead", expression = "java(notification.isRead())")
    NotificationDTO toDto(Notification notification);
}
