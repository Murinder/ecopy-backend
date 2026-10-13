package com.example.eventservice.mapper;

import com.example.eventservice.dto.CreateEventDto;
import com.example.eventservice.dto.UpdateEventDto;
import com.example.eventservice.model.Event;
import com.example.sharedlib.dto.EventDto;
import org.mapstruct.Mapper;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface EventMapper {
    EventDto toDto(Event event);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "applications", ignore = true)
    @Mapping(target = "teams", ignore = true)
    @Mapping(target = "tasks", ignore = true)
    @Mapping(target = "mentors", ignore = true)
    Event toEntity(CreateEventDto createEventDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "applications", ignore = true)
    @Mapping(target = "teams", ignore = true)
    @Mapping(target = "tasks", ignore = true)
    @Mapping(target = "mentors", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(UpdateEventDto updateEventDto, @MappingTarget Event event);
}
