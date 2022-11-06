package hu.bme.mit.swsv.itssos.itscentral;

import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import eu.arrowhead.client.skeleton.provider.ItsCentralApplicationInitListener;
import eu.arrowhead.common.Defaults;
import eu.arrowhead.common.Utilities;
import hu.bme.mit.swsv.itssos.itscentral.logic.SensorMessageType;
import hu.bme.mit.swsv.itssos.itscentral.logic.VehicleMessageType;
import hu.bme.mit.swsv.itssos.vehicle.SendNotificationRequestDto;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.method.P;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static eu.arrowhead.common.CommonConstants.ECHO_URI;
import static hu.bme.mit.swsv.itssos.itscentral.ItsCentralProviderInternalConstants.ITSCENTRAL_URI;
import static hu.bme.mit.swsv.itssos.itscentral.ItsCentralProviderInternalConstants.REPORT_TRAIN_FULL_URI;
import static hu.bme.mit.swsv.itssos.itscentral.ItsCentralProviderInternalConstants.REPORT_VEHICLE_FULL_URI;
import static hu.bme.mit.swsv.itssos.itscentral.logic.NotificationLevel.*;
import static hu.bme.mit.swsv.itssos.itscentral.logic.SensorMessageType.*;
import static hu.bme.mit.swsv.itssos.itscentral.logic.SensorType.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.Is.is;
import java.util.ArrayList;
import static hu.bme.mit.swsv.itssos.itscentral.logic.VehicleMessageType.*;

@RunWith(SpringRunner.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class ItsCentralIntegrationTest {

    // =================================================================================================
    // members
    private static final Logger logger = LogManager.getLogger(ItsCentralIntegrationTest.class);

    private static final String SEND_NOTIFICATION_URI = "/vehicle-communicator/send-notification";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    private ItsCentralApplicationInitListener initListener;

    @Autowired
    private ItsCentralController controller;

    @Rule
    public WireMockRule serviceRegistryMock = new WireMockRule(
            options().notifier(new ConsoleNotifier(true)).port(Defaults.DEFAULT_SERVICE_REGISTRY_PORT));
    @Rule
    public WireMockRule orchestratorMock = new WireMockRule(
            options().notifier(new ConsoleNotifier(true)).port(Defaults.DEFAULT_ORCHESTRATOR_PORT));
    @Rule
    public WireMockRule vehicleCommunicatorMock = new WireMockRule(
            options().notifier(new ConsoleNotifier(true)).port(8889));

    private ReportTrainRequestDto proximityRegisterPayload = new ReportTrainRequestDto(PROXIMITY, REGISTER);
    private ReportTrainRequestDto proximityArrivingPayloadA = new ReportTrainRequestDto(PROXIMITY,
            SensorMessageType.ARRIVING);
    private ReportTrainRequestDto proximityArrivingPayloadB = new ReportTrainRequestDto(PROXIMITY,
            SensorMessageType.ARRIVING);
    private ReportTrainRequestDto proximityLeftPayload = new ReportTrainRequestDto(PROXIMITY, SensorMessageType.LEFT);
    private ReportTrainRequestDto timetableRegisterPayload = new ReportTrainRequestDto(TIMETABLE, REGISTER);
    private ReportTrainRequestDto timetableLeftPayload = new ReportTrainRequestDto(TIMETABLE, SensorMessageType.LEFT);
    private ReportTrainRequestDto timetableArrivingPayload = new ReportTrainRequestDto(TIMETABLE,
            SensorMessageType.ARRIVING);
    private ArrayList<ReportVehicleRequestDto> vehicles = new ArrayList<ReportVehicleRequestDto>();
    private ArrayList<String> licensePlates = new ArrayList<String>() {
        {
            add("ABC-123");
            add("DEF-456");
        }
    };

    // =================================================================================================
    // methods

    public void addVehiclesToIntersection() {
        for (String vehicle : licensePlates) {
            ReportVehicleRequestDto vehicle_payload = new ReportVehicleRequestDto(vehicle, VehicleMessageType.ARRIVING);
            controller.reportVehicle(vehicle_payload);
            vehicles.add(vehicle_payload);
        }
    }

    public void assertThatStatusIsOKAndContainsString(ResponseEntity<String> response, String str) {
        assertThat(response.getStatusCodeValue(), is(200));
        assertThat(response.getBody(), containsString(str));
    }

    @Before
    public void setup() {
        logger.info("setup: START");

        vehicleCommunicatorMock.stubFor(post(urlPathEqualTo(SEND_NOTIFICATION_URI))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("OK")));

        initListener.init(applicationContext);
        controller.init();

        logger.info("setup: END");
    }

    // -------------------------------------------------------------------------------------------------
    @Test
    public void testItsCentralEcho() {
        logger.info("testItsCentralEcho: START");
        

        // Act
        ResponseEntity<String> response = restTemplate.getForEntity(ITSCENTRAL_URI + ECHO_URI, String.class);

        // Assert
        assertThat(response.getStatusCodeValue(), is(200));
        assertThat(response.getBody(), containsString("Got it!"));
        logger.info("testItsCentralEcho: END");
    }

    @Test
    public void testProximityRegister() {
        logger.info("testProximityRegister: START");
        // Arrange
        ReportTrainRequestDto payload = new ReportTrainRequestDto(PROXIMITY, REGISTER);

        // Act
        ResponseEntity<String> response = restTemplate.postForEntity(REPORT_TRAIN_FULL_URI, payload, String.class);

        // Assert
        assertThat(response.getStatusCodeValue(), is(200));
        assertThat(response.getBody(), containsString("OK"));

        String expectedNotification = Utilities.toJson(new SendNotificationRequestDto(null, PASS_SLOWLY));
        vehicleCommunicatorMock.verify(1, postRequestedFor(urlEqualTo(SEND_NOTIFICATION_URI))
                .withRequestBody(equalToJson(expectedNotification))
                .withHeader("Content-Type", equalTo("application/json")));
        logger.info("testProximityRegister: END");
    }

    /* Test Scenario 3 */
    @Test
    public void testProximityArriving() {
        logger.info("testProximityArriving: START");

        // Arrange
        addVehiclesToIntersection();

        // Act
        ResponseEntity<String> response = restTemplate.postForEntity(REPORT_TRAIN_FULL_URI, proximityArrivingPayloadA,
                String.class);

        // Assert
        assertThatStatusIsOKAndContainsString(response, "OK");

        for (String plate : licensePlates) {
            String expectedNotification = Utilities.toJson(new SendNotificationRequestDto(plate, STOP));
            vehicleCommunicatorMock.verify(1, postRequestedFor(urlEqualTo(SEND_NOTIFICATION_URI))
                    .withRequestBody(equalToJson(expectedNotification))
                    .withHeader("Content-Type", equalTo("application/json")));
        }

        String expectedBroadcastNotification = Utilities.toJson(new SendNotificationRequestDto(null, STOP));
        vehicleCommunicatorMock.verify(1, postRequestedFor(urlEqualTo(SEND_NOTIFICATION_URI))
                .withRequestBody(equalToJson(expectedBroadcastNotification))
                .withHeader("Content-Type", equalTo("application/json")));
        logger.info("testProximityArriving: END");
    }

    /*Test Scenario 6*/
    @Test
    public void testVehicleArrivingTwoTimes(){

        logger.info("testVehicleArrivingTwoTimes: START");

        // Arrange
        ReportVehicleRequestDto vehiclePayload = new ReportVehicleRequestDto("GHI-567", VehicleMessageType.ARRIVING);

        // Act
        ResponseEntity<String> registerResponseFirst = restTemplate.postForEntity(REPORT_VEHICLE_FULL_URI, vehiclePayload, String.class);
        ResponseEntity<String> registerResponseSecond = restTemplate.postForEntity(REPORT_VEHICLE_FULL_URI, vehiclePayload, String.class);

        // Assert
        assertThat(registerResponseFirst.getStatusCodeValue(), is(200));
        assertThat(registerResponseFirst.getBody(), is("true"));

        assertThat(registerResponseSecond.getStatusCodeValue(), is(200));
        assertThat(registerResponseSecond.getBody(), is("false"));

        logger.info("testVehicleArrivingTwoTimes: END");
    }

    /*Test Scenario 7*/
    @Test
    public void testRegistrationTwoTimesDuringTrainArrives() {

        logger.info("testRegistrationTwoTimesDuringTrainArrives: START");

        // Act
        ResponseEntity<String> registerResponseFirst = restTemplate.postForEntity(REPORT_TRAIN_FULL_URI, proximityRegisterPayload, String.class);
        ResponseEntity<String> arrivingResponse = restTemplate.postForEntity(REPORT_TRAIN_FULL_URI, proximityArrivingPayloadA, String.class);
        ResponseEntity<String> registerResponseSecond = restTemplate.postForEntity(REPORT_TRAIN_FULL_URI, proximityRegisterPayload, String.class);

        // Assert
        assertThatStatusIsOKAndContainsString(registerResponseFirst, "OK");
        
        assertThatStatusIsOKAndContainsString(arrivingResponse, "OK");
        
        assertThatStatusIsOKAndContainsString(registerResponseSecond, "OK");
        
        String expectedLookAroundNotification = Utilities.toJson(new SendNotificationRequestDto(null, LOOK_AROUND));
        vehicleCommunicatorMock.verify(1, postRequestedFor(urlEqualTo(SEND_NOTIFICATION_URI))
                .withRequestBody(equalToJson(expectedLookAroundNotification))
                .withHeader("Content-Type", equalTo("application/json")));

        String expectedStopNotification = Utilities.toJson(new SendNotificationRequestDto(null, STOP));
        vehicleCommunicatorMock.verify(1, postRequestedFor(urlEqualTo(SEND_NOTIFICATION_URI))
                .withRequestBody(equalToJson(expectedStopNotification))
                .withHeader("Content-Type", equalTo("application/json")));

        logger.info("testRegistrationTwoTimesDuringTrainArrives: END");
    }


}
