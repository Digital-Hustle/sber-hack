package ru.proxodnaya.people.features.staff.command.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import ru.proxodnaya.people.config.properties.StaffProperties;

/**
 * При старте заводит первого администратора ({@code STAFF_BOOTSTRAP_ADMIN_EMAIL}), если персонала нет.
 */
@Component
public class StaffBootstrap implements ApplicationRunner {

    private final StaffCommandService staff;
    private final StaffProperties props;

    public StaffBootstrap(StaffCommandService staff, StaffProperties props) {
        this.staff = staff;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        staff.bootstrap(props.bootstrapEmail());
    }
}
