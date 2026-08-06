package com.gooroomees.neulbomgil_backend.global.config;

import com.gooroomees.neulbomgil_backend.domain.map.service.FacilityDataInitService;
import com.gooroomees.neulbomgil_backend.domain.map.service.ParkDataInitService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final ParkDataInitService parkDataInitService;
    private final FacilityDataInitService facilityDataInitService;

    @Override
    public void run(ApplicationArguments args) {
        parkDataInitService.initParkData();
        facilityDataInitService.refreshFacilities();
    }
}