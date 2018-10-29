/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.gateway.services.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.common.data.domain.Page;
import io.gravitee.gateway.env.GatewayConfiguration;
import io.gravitee.gateway.handlers.api.definition.Api;
import io.gravitee.gateway.handlers.api.manager.ApiManager;
import io.gravitee.gateway.services.sync.builder.RepositoryApiBuilder;
import io.gravitee.repository.exceptions.TechnicalException;
import io.gravitee.repository.management.api.ApiRepository;
import io.gravitee.repository.management.api.EventRepository;
import io.gravitee.repository.management.api.PlanRepository;
import io.gravitee.repository.management.api.search.ApiFieldExclusionFilter;
import io.gravitee.repository.management.api.search.EventCriteria;
import io.gravitee.repository.management.api.search.Pageable;
import io.gravitee.repository.management.model.Event;
import io.gravitee.repository.management.model.EventType;
import io.gravitee.repository.management.model.LifecycleState;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class SyncManagerTest {

    @InjectMocks
    private SyncManager syncManager = new SyncManager();

    @Mock
    private ApiRepository apiRepository;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private ApiManager apiManager;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private GatewayConfiguration gatewayConfiguration;

    @Before
    public void setUp() {
        when(gatewayConfiguration.shardingTags()).thenReturn(Optional.empty());
    }

    @Test
    public void test_empty() throws TechnicalException {
        when(apiRepository.search(null, new ApiFieldExclusionFilter.Builder().excludeDefinition().excludePicture().build())).thenReturn(emptyList());

        syncManager.refresh();

        verify(apiManager, never()).deploy(any(Api.class));
        verify(apiManager, never()).update(any(Api.class));
        verify(apiManager, never()).undeploy(any(String.class));
    }

    @Test
    public void test_newApi() throws Exception {
        io.gravitee.repository.management.model.Api api =
                new RepositoryApiBuilder().id("api-test").updatedAt(new Date()).definition("test").build();

        final io.gravitee.definition.model.Api mockApi = mockApi(api);
        
        final Event mockEvent = mockEvent(api, EventType.PUBLISH_API);
        when(eventRepository.search(
                any(EventCriteria.class),
                any(Pageable.class)
        )).thenReturn(new Page<>(singletonList(mockEvent), 0, 0, 1));

        when(apiRepository.search(null, new ApiFieldExclusionFilter.Builder().excludeDefinition().excludePicture().build())).thenReturn(singletonList(api));

        syncManager.refresh();

        verify(apiManager).deploy(new Api(mockApi));
        verify(apiManager, never()).update(any(Api.class));
        verify(apiManager, never()).undeploy(any(String.class));
    }

    @Test
    public void test_twiceWithSameApi() throws Exception {
        io.gravitee.repository.management.model.Api api =
                new RepositoryApiBuilder().id("api-test").updatedAt(new Date()).definition("test").build();

        final io.gravitee.definition.model.Api mockApi = mockApi(api);
        final Event mockEvent = mockEvent(api, EventType.PUBLISH_API);

        when(eventRepository.search(
                any(EventCriteria.class),
                any(Pageable.class)
        )).thenReturn(new Page<>(singletonList(mockEvent), 0, 0, 1));

        when(apiRepository.search(null, new ApiFieldExclusionFilter.Builder().excludeDefinition().excludePicture().build())).thenReturn(singletonList(api));
        when(apiManager.get(api.getId())).thenReturn(null);

        syncManager.refresh();

        final Api apiDefinition = new Api(mockApi);
        apiDefinition.setEnabled(api.getLifecycleState() == LifecycleState.STARTED);
        apiDefinition.setDeployedAt(api.getDeployedAt());
        when(apiManager.get(api.getId())).thenReturn(apiDefinition);

        syncManager.refresh();

        verify(apiManager).deploy(new Api(mockApi));
        verify(apiManager, never()).update(any(Api.class));
        verify(apiManager, never()).undeploy(any(String.class));
    }

    @Test
    public void test_twiceWithTwoApis() throws Exception {
        io.gravitee.repository.management.model.Api api =
                new RepositoryApiBuilder().id("api-test").updatedAt(new Date()).definition("test").build();

        final io.gravitee.definition.model.Api mockApi = mockApi(api);

        final Event mockEvent = mockEvent(api, EventType.PUBLISH_API);

        when(eventRepository.search(
                any(EventCriteria.class),
                any(Pageable.class)
        )).thenReturn(new Page<>(singletonList(mockEvent), 0, 0, 1));

        when(apiRepository.search(null, new ApiFieldExclusionFilter.Builder().excludeDefinition().excludePicture().build())).thenReturn(singletonList(api));

        syncManager.refresh();

        io.gravitee.repository.management.model.Api api2 =
                new RepositoryApiBuilder().id("api-test-2").updatedAt(new Date()).definition("test2").build();

        final io.gravitee.definition.model.Api mockApi2 = mockApi(api2);
        final Event mockEvent2 = mockEvent(api2, EventType.PUBLISH_API);

        List<Event> events = new ArrayList<>();
        events.add(mockEvent2);

        when(eventRepository.search(
                any(EventCriteria.class)
        )).thenReturn(events);

        syncManager.refresh();

        verify(apiManager, times(2)).deploy(argThat(new ArgumentMatcher<Api>() {
            @Override
            public boolean matches(Object argument) {
                final Api api = (Api) argument;
                return api.getId().equals(mockApi.getId()) || api2.getId().equals(mockApi2.getId());
            }
        }));
        verify(apiManager, never()).update(any(Api.class));
        verify(apiManager, never()).undeploy(any(String.class));
    }

    @Test
    public void test_twiceWithTwoApis_apiToRemove() throws Exception {
        io.gravitee.repository.management.model.Api api =
                new RepositoryApiBuilder().id("api-test").updatedAt(new Date()).definition("test").build();

        final io.gravitee.definition.model.Api mockApi = mockApi(api);

        final Event mockEvent = mockEvent(api, EventType.PUBLISH_API);

        when(eventRepository.search(
                any(EventCriteria.class),
                any(Pageable.class)
        )).thenReturn(new Page<>(singletonList(mockEvent), 0, 0, 1));

        when(apiRepository.search(null, new ApiFieldExclusionFilter.Builder().excludeDefinition().excludePicture().build())).thenReturn(singletonList(api));

        syncManager.refresh();

        io.gravitee.repository.management.model.Api api2 =
                new RepositoryApiBuilder().id("api-test-2").updatedAt(new Date()).definition("test2").build();
        final io.gravitee.definition.model.Api mockApi2 = mockApi(api2);
        final Event mockEvent2 = mockEvent(api2, EventType.PUBLISH_API);

        when(eventRepository.search(
                any(EventCriteria.class)
        )).thenReturn(Collections.singletonList(mockEvent));

        syncManager.refresh();

        verify(apiManager, times(2)).deploy(argThat(new ArgumentMatcher<Api>() {
            @Override
            public boolean matches(Object argument) {
                final Api api = (Api) argument;
                return api.getId().equals(mockApi.getId()) || api2.getId().equals(mockApi2.getId());
            }
        }));
        verify(apiManager, never()).update(any(Api.class));
        verify(apiManager, never()).undeploy(api.getId());
        verify(apiManager, never()).undeploy(api2.getId());
    }

    @Test
    public void test_twiceWithTwoApis_apiToUpdate() throws Exception {
        io.gravitee.repository.management.model.Api api =
                new RepositoryApiBuilder().id("api-test").updatedAt(new Date()).definition("test").build();

        Instant updateDateInst = api.getUpdatedAt().toInstant().plus(Duration.ofHours(1));
        io.gravitee.repository.management.model.Api api2 =
                new RepositoryApiBuilder().id("api-test").updatedAt(Date.from(updateDateInst)).definition("test2").build();

        final io.gravitee.definition.model.Api mockApi = mockApi(api);
        mockApi(api2);

        final Event mockEvent = mockEvent(api, EventType.PUBLISH_API);
        final Event mockEvent2 = mockEvent(api2, EventType.PUBLISH_API);

        List<Event> events = new ArrayList<>();
        events.add(mockEvent);
        events.add(mockEvent2);

        when(eventRepository.search(
                any(EventCriteria.class),
                any(Pageable.class)
        )).thenReturn(new Page<>(events, 0, 0, 1));

        when(apiRepository.search(null, new ApiFieldExclusionFilter.Builder().excludeDefinition().excludePicture().build())).thenReturn(singletonList(api));

        syncManager.refresh();

        when(eventRepository.search(
                any(EventCriteria.class)
        )).thenReturn(Collections.singletonList(mockEvent2));

        final Api apiDefinition = new Api(mockApi);
        apiDefinition.setEnabled(api.getLifecycleState() == LifecycleState.STARTED);
        apiDefinition.setDeployedAt(api.getDeployedAt());
        when(apiManager.get(api.getId())).thenReturn(apiDefinition);

        syncManager.refresh();

        verify(apiManager).deploy(apiDefinition);
        verify(apiManager).update(apiDefinition);
        verify(apiManager, never()).undeploy(any(String.class));
    }

    @Test
    public void test_twiceWithTwoApis_api_noUpdate() throws Exception {
        io.gravitee.repository.management.model.Api api =
                new RepositoryApiBuilder().id("api-test").updatedAt(new Date()).definition("test").build();
        io.gravitee.repository.management.model.Api api2 =
                new RepositoryApiBuilder().id("api-test").updatedAt(api.getUpdatedAt()).definition("test").build();

        final io.gravitee.definition.model.Api mockApi = mockApi(api);

        final Event mockEvent = mockEvent(api, EventType.PUBLISH_API);

        final Event mockEvent2 = mockEvent(api2, EventType.PUBLISH_API);

        List<Event> events = new ArrayList<Event>();
        events.add(mockEvent);
        events.add(mockEvent2);

        when(eventRepository.search(
                any(EventCriteria.class),
                any(Pageable.class)
        )).thenReturn(new Page<>(events, 0, 0, 1));

        when(apiRepository.search(null, new ApiFieldExclusionFilter.Builder().excludeDefinition().excludePicture().build())).thenReturn(singletonList(api));

        syncManager.refresh();

        when(eventRepository.search(
                any(EventCriteria.class)
        )).thenReturn(Collections.singletonList(mockEvent2));

        syncManager.refresh();

        verify(apiManager, times(2)).deploy(new Api(mockApi));
        verify(apiManager, never()).update(any(Api.class));
        verify(apiManager, never()).undeploy(any(String.class));
    }

    @Test
    public void test_deployApiWithTag() throws Exception {
        shouldDeployApiWithTags("test,toto", new String[]{"test"});
    }

    @Test
    public void test_deployApiWithUpperCasedTag() throws Exception {
        shouldDeployApiWithTags("test,toto", new String[]{"Test"});
    }

    @Test
    public void test_deployApiWithAccentTag() throws Exception {
        shouldDeployApiWithTags("test,toto", new String[]{"tést"});
    }

    @Test
    public void test_deployApiWithUpperCasedAndAccentTag() throws Exception {
        shouldDeployApiWithTags("test", new String[]{"Tést"});
    }

    @Test
    public void test_deployApiWithTagExclusion() throws Exception {
        shouldDeployApiWithTags("test,!toto", new String[]{"test"});
    }

    @Test
    public void test_deployApiWithSpaceAfterComma() throws Exception {
        shouldDeployApiWithTags("test, !toto", new String[]{"test"});
    }

    @Test
    public void test_deployApiWithSpaceBeforeComma() throws Exception {
        shouldDeployApiWithTags("test ,!toto", new String[]{"test"});
    }

    @Test
    public void test_deployApiWithSpaceBeforeTag() throws Exception {
        shouldDeployApiWithTags(" test,!toto", new String[]{"test"});
    }

    public void shouldDeployApiWithTags(final String tags, final String[] apiTags) throws Exception {
        io.gravitee.repository.management.model.Api api =
                new RepositoryApiBuilder().id("api-test").updatedAt(new Date()).definition("test").build();

        final io.gravitee.definition.model.Api mockApi = mockApi(api, apiTags);

        when(gatewayConfiguration.shardingTags()).thenReturn(Optional.of(Arrays.asList(tags.split(","))));
        when(apiRepository.search(null, new ApiFieldExclusionFilter.Builder().excludeDefinition().excludePicture().build())).thenReturn(singletonList(api));
        when(apiManager.apis()).thenReturn(Collections.singleton(new Api(mockApi)));

        final Event mockEvent = mockEvent(api, EventType.PUBLISH_API);
        when(eventRepository.search(any(EventCriteria.class), any(Pageable.class)))
                .thenReturn(new Page<>(singletonList(mockEvent), 0, 0, 1));

        syncManager.refresh();

        verify(apiManager).deploy(new Api(mockApi));
        verify(apiManager, never()).update(any(Api.class));
        verify(apiManager, never()).undeploy(any(String.class));
    }

    @Test
    public void test_not_deployApiWithTagExclusion() throws Exception {
        io.gravitee.repository.management.model.Api api =
                new RepositoryApiBuilder().id("api-test").updatedAt(new Date()).definition("test").build();

        final io.gravitee.definition.model.Api mockApi = mockApi(api);
        mockApi.setTags(new HashSet<>(Arrays.asList(new String[]{"test"})));

        when(gatewayConfiguration.shardingTags()).thenReturn(Optional.of(singletonList("!test")));
        when(apiRepository.search(null, new ApiFieldExclusionFilter.Builder().excludeDefinition().excludePicture().build())).thenReturn(singletonList(api));

        final Event mockEvent = mockEvent(api, EventType.PUBLISH_API);
        when(eventRepository.search(
                any(EventCriteria.class),
                any(Pageable.class)
        )).thenReturn(new Page<>(singletonList(mockEvent), 0, 0, 1));

        syncManager.refresh();

        verify(apiManager, never()).deploy(new Api(mockApi));
        verify(apiManager, never()).update(any(Api.class));
    }

    @Test
    public void test_deployApiWithTagInclusionExclusion() throws Exception {
        io.gravitee.repository.management.model.Api api =
                new RepositoryApiBuilder().id("api-test").updatedAt(new Date()).definition("test").build();
//        api.setTags(new HashSet<>(Arrays.asList(new String[]{"test", "toto"})));

        final io.gravitee.definition.model.Api mockApi = mockApi(api);

        when(gatewayConfiguration.shardingTags()).thenReturn(Optional.of(Arrays.asList("!test", "toto")));
        when(apiRepository.search(null, new ApiFieldExclusionFilter.Builder().excludeDefinition().excludePicture().build())).thenReturn(singletonList(api));
        when(apiManager.apis()).thenReturn(Collections.singleton(new Api(mockApi)));

        final Event mockEvent = mockEvent(api, EventType.PUBLISH_API);
        when(eventRepository.search(
                any(EventCriteria.class),
                any(Pageable.class)
        )).thenReturn(new Page<>(singletonList(mockEvent), 0, 0, 1));

        syncManager.refresh();

        verify(apiManager).deploy(new Api(mockApi));
        verify(apiManager, never()).update(any(Api.class));
        verify(apiManager, never()).undeploy(any(String.class));
    }

    @Test
    public void test_not_deployApiWithoutTag() throws Exception {
        io.gravitee.repository.management.model.Api api =
                new RepositoryApiBuilder().id("api-test").updatedAt(new Date()).definition("test").build();

        final io.gravitee.definition.model.Api mockApi = mockApi(api);

        when(gatewayConfiguration.shardingTags()).thenReturn(Optional.of(Arrays.asList("test", "toto")));
        when(apiRepository.search(null, new ApiFieldExclusionFilter.Builder().excludeDefinition().excludePicture().build())).thenReturn(singletonList(api));

        final Event mockEvent = mockEvent(api, EventType.PUBLISH_API);
        when(eventRepository.search(
                any(EventCriteria.class),
                any(Pageable.class)
        )).thenReturn(new Page<>(singletonList(mockEvent), 0, 0, 1));

        syncManager.refresh();

        verify(apiManager, never()).deploy(new Api(mockApi));
        verify(apiManager, never()).update(any(Api.class));
    }

    @Test(expected = IllegalArgumentException.class)
    @Ignore
    public void shouldNotDeployBecauseWrongConfiguration() throws Exception {
        io.gravitee.repository.management.model.Api api =
                new RepositoryApiBuilder().id("api-test").updatedAt(new Date()).definition("test").build();

        final io.gravitee.definition.model.Api mockApi = mockApi(api);

        when(gatewayConfiguration.shardingTags()).thenReturn(Optional.of(Arrays.asList("test", "!test")));
        when(apiRepository.search(null, new ApiFieldExclusionFilter.Builder().excludeDefinition().excludePicture().build())).thenReturn(singletonList(api));
        when(apiManager.apis()).thenReturn(Collections.singleton(new Api(mockApi)));

        final Event mockEvent = mockEvent(api, EventType.PUBLISH_API);
        when(eventRepository.search(
                eq(new EventCriteria.Builder()
                        .types(EventType.PUBLISH_API, EventType.UNPUBLISH_API, EventType.START_API, EventType.STOP_API)
                        .property(Event.EventProperties.API_ID.getValue(), api.getId())
                        .build()), any()
        )).thenReturn((new Page(singletonList(mockEvent), 0, 0, 0)));

        syncManager.refresh();
    }

    @Test
    public void test_not_deployApiWithoutEvent() throws Exception {
        io.gravitee.repository.management.model.Api api = new RepositoryApiBuilder().id("api-test").updatedAt(new Date()).definition("test").build();

        final io.gravitee.definition.model.Api mockApi = mockApi(api);

        when(eventRepository.search(
                any(EventCriteria.class),
                any(Pageable.class)
        )).thenReturn(new Page<>(Collections.emptyList(), 0, 0, 0));

        when(apiRepository.search(null, new ApiFieldExclusionFilter.Builder().excludeDefinition().excludePicture().build())).thenReturn(singletonList(api));

        syncManager.refresh();

        verify(apiManager, never()).deploy(new Api(mockApi));
        verify(apiManager, never()).update(any(Api.class));
        verify(apiManager, never()).undeploy(any(String.class));
    }

    @Test
    public void test_deployOnlyOneApiWithTwoApisAndOneEvent() throws Exception {
        io.gravitee.repository.management.model.Api api = new RepositoryApiBuilder().id("api-test").updatedAt(new Date()).definition("test").build();
        io.gravitee.repository.management.model.Api api2 = new RepositoryApiBuilder().id("api-test-2").updatedAt(new Date()).definition("test2").build();

        final io.gravitee.definition.model.Api mockApi = mockApi(api);

        final List<io.gravitee.repository.management.model.Api> apis = new ArrayList<>();
        apis.add(api);
        apis.add(api2);

        final Event mockEvent = mockEvent(api, EventType.PUBLISH_API);
        when(eventRepository.search(
                any(EventCriteria.class),
                any(Pageable.class)
        )).thenReturn(
                new Page<>(Collections.emptyList(), 0, 0, 0),
                new Page<>(singletonList(mockEvent), 0, 0, 1));

        when(apiRepository.search(null, new ApiFieldExclusionFilter.Builder().excludeDefinition().excludePicture().build())).thenReturn(apis);

        syncManager.refresh();

        verify(apiManager).deploy(argThat(new ArgumentMatcher<Api>() {
            @Override
            public boolean matches(Object argument) {
                final Api api = (Api) argument;
                return api.getId().equals(mockApi.getId());
            }
        }));
        verify(apiManager, never()).update(any(Api.class));
        verify(apiManager, never()).undeploy(any(String.class));
    }

    @Test
    public void test_shouldUndeployIfLastEventIsUnpublishAPI() throws Exception {
        io.gravitee.repository.management.model.Api api = new RepositoryApiBuilder().id("api-test").updatedAt(new Date()).definition("test").build();

        final io.gravitee.definition.model.Api mockApi = mockApi(api);

        final Event mockEvent = mockEvent(api, EventType.PUBLISH_API);
        final Event mockEvent2 = mockEvent(api, EventType.UNPUBLISH_API);

        when(eventRepository.search(
                any(EventCriteria.class),
                any(Pageable.class)
        )).thenReturn(new Page<>(singletonList(mockEvent), 0, 0, 1), new Page<>(singletonList(mockEvent2), 0, 0, 1));

        when(apiRepository.search(null, new ApiFieldExclusionFilter.Builder().excludeDefinition().excludePicture().build())).thenReturn(singletonList(api));

        syncManager.refresh();

        when(eventRepository.search(
                any(EventCriteria.class)
        )).thenReturn(singletonList(mockEvent2));

        syncManager.refresh();

        verify(apiManager).deploy(new Api(mockApi));
        verify(apiManager, never()).update(any(Api.class));
        verify(apiManager).undeploy(mockApi.getId());
    }


    @Test
    public void test_shouldUpdateIfLastEventIsStartAPI() throws Exception {
        io.gravitee.repository.management.model.Api api = new RepositoryApiBuilder()
                                                            .id("api-test")
                                                            .updatedAt(new Date())
                                                            .definition("test")
                                                            .lifecycleState(LifecycleState.STOPPED)
                                                            .build();

        Instant updateDateInst = api.getUpdatedAt().toInstant().plus(Duration.ofHours(1));
        io.gravitee.repository.management.model.Api api2 = new RepositoryApiBuilder()
                                                            .id("api-test")
                                                            .updatedAt(Date.from(updateDateInst))
                                                            .definition("test")
                                                            .lifecycleState(LifecycleState.STARTED)
                                                            .build();

        final io.gravitee.definition.model.Api mockApi = mockApi(api);
        mockApi(api2);
        final Event mockEvent = mockEvent(api, EventType.PUBLISH_API);
        final Event mockEvent2 = mockEvent(api2, EventType.START_API);

        List<Event> events = new ArrayList<Event>();
        events.add(mockEvent);
        events.add(mockEvent2);

        when(eventRepository.search(
                any(EventCriteria.class),
                any(Pageable.class)
        )).thenReturn(new Page<>(singletonList(mockEvent), 0, 0, 1));

        when(apiRepository.search(null, new ApiFieldExclusionFilter.Builder().excludeDefinition().excludePicture().build())).thenReturn(singletonList(api));

        syncManager.refresh();

        final Api apiDefinition = new Api(mockApi);
        apiDefinition.setEnabled(api.getLifecycleState() == LifecycleState.STARTED);
        apiDefinition.setDeployedAt(api.getDeployedAt());
        when(apiManager.get(api.getId())).thenReturn(apiDefinition);

        when(eventRepository.search(
                any(EventCriteria.class)
        )).thenReturn(singletonList(mockEvent2));

        syncManager.refresh();

        verify(apiManager).deploy(new Api(mockApi));
        verify(apiManager).update(new Api(mockApi));
        verify(apiManager, never()).undeploy(any(String.class));
    }

    @Test
    public void test_shouldUpdateIfLastEventIsStopAPI() throws Exception {
        io.gravitee.repository.management.model.Api api = new RepositoryApiBuilder()
                                                            .id("api-test")
                                                            .updatedAt(new Date())
                                                            .definition("test")
                                                            .lifecycleState(LifecycleState.STARTED)
                                                            .build();

        Instant updateDateInst = api.getUpdatedAt().toInstant().plus(Duration.ofHours(1));
        io.gravitee.repository.management.model.Api api2 = new RepositoryApiBuilder()
                                                            .id("api-test")
                                                            .updatedAt(Date.from(updateDateInst))
                                                            .definition("test")
                                                            .lifecycleState(LifecycleState.STOPPED)
                                                            .build();

        final io.gravitee.definition.model.Api mockApi = mockApi(api);
        mockApi(api2);
        final Event mockEvent = mockEvent(api, EventType.PUBLISH_API);
        final Event mockEvent2 = mockEvent(api2, EventType.STOP_API);

        List<Event> events = new ArrayList<Event>();
        events.add(mockEvent);
        events.add(mockEvent2);

        when(eventRepository.search(
                any(EventCriteria.class),
                any(Pageable.class)
        )).thenReturn(new Page<>(singletonList(mockEvent), 0, 0, 1));

        when(apiRepository.search(null, new ApiFieldExclusionFilter.Builder().excludeDefinition().excludePicture().build())).thenReturn(singletonList(api));
        when(apiManager.apis()).thenReturn(Collections.singleton(new Api(mockApi)));

        syncManager.refresh();

        when(eventRepository.search(
                any(EventCriteria.class)
        )).thenReturn(singletonList(mockEvent));

        final Api apiDefinition = new Api(mockApi);
        apiDefinition.setEnabled(api.getLifecycleState() == LifecycleState.STARTED);
        apiDefinition.setDeployedAt(api.getDeployedAt());
        when(apiManager.get(api.getId())).thenReturn(apiDefinition);

        syncManager.refresh();

        verify(apiManager).deploy(new Api(mockApi));
        verify(apiManager).update(new Api(mockApi));
        verify(apiManager, never()).undeploy(any(String.class));
    }

    private io.gravitee.definition.model.Api mockApi(final io.gravitee.repository.management.model.Api api) throws Exception {
        return mockApi(api, new String[]{});
    }

    private io.gravitee.definition.model.Api mockApi(final io.gravitee.repository.management.model.Api api, final String[] tags) throws Exception {
        final io.gravitee.definition.model.Api mockApi = new io.gravitee.definition.model.Api();
        mockApi.setId(api.getId());
        mockApi.setTags(new HashSet<>(Arrays.asList(tags)));
        when(objectMapper.readValue(api.getDefinition(), io.gravitee.definition.model.Api.class)).thenReturn(mockApi);
        return mockApi;
    }
    
    private Event mockEvent(final io.gravitee.repository.management.model.Api api, EventType eventType) throws Exception {
        Map<String, String> properties = new HashMap<>();
        properties.put(Event.EventProperties.API_ID.getValue(), api.getId());

        Event event = new Event();
        event.setType(eventType);
        event.setCreatedAt(new Date());
        event.setProperties(properties);

        when(objectMapper.readValue(event.getPayload(), io.gravitee.repository.management.model.Api.class)).thenReturn(api);

        return event;
    }
}
