
package br.com.conductor.heimdall.core.service;

/*-
 * =========================LICENSE_START==================================
 * heimdall-core
 * ========================================================================
 * Copyright (C) 2018 Conductor Tecnologia SA
 * ========================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ==========================LICENSE_END===================================
 */

import br.com.conductor.heimdall.core.converter.GenericConverter;
import br.com.conductor.heimdall.core.converter.InterceptorMap;
import br.com.conductor.heimdall.core.dto.*;
import br.com.conductor.heimdall.core.dto.interceptor.OAuthDTO;
import br.com.conductor.heimdall.core.dto.interceptor.RateLimitDTO;
import br.com.conductor.heimdall.core.dto.page.InterceptorPage;
import br.com.conductor.heimdall.core.entity.*;
import br.com.conductor.heimdall.core.enums.InterceptorLifeCycle;
import br.com.conductor.heimdall.core.enums.Status;
import br.com.conductor.heimdall.core.enums.TypeInterceptor;
import br.com.conductor.heimdall.core.exception.ExceptionMessage;
import br.com.conductor.heimdall.core.exception.HeimdallException;
import br.com.conductor.heimdall.core.repository.*;
import br.com.conductor.heimdall.core.service.amqp.AMQPInterceptorService;
import br.com.conductor.heimdall.core.util.JsonUtils;
import br.com.conductor.heimdall.core.util.Pageable;
import br.com.conductor.heimdall.core.util.StringUtils;
import br.com.conductor.heimdall.core.util.TemplateUtils;
import br.com.twsoftware.alfred.object.Objeto;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.ExampleMatcher.StringMatcher;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static br.com.conductor.heimdall.core.exception.ExceptionMessage.*;
import static br.com.conductor.heimdall.core.util.Constants.MIDDLEWARE_API_ROOT;
import static br.com.twsoftware.alfred.object.Objeto.isBlank;

/**
 * This class provides methos to create, read, update and delete a {@link Interceptor} resource.<br/>
 * This class also performs a validation  before it saves or deletes a {@link Interceptor}.
 *
 * @author Filipe Germano
 * @author Marcos Filho
 */
@Slf4j
@Service
public class InterceptorService {

    @Autowired
    private InterceptorRepository interceptorRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private ResourceRepository resourceRepository;

    @Autowired
    private OperationRepository operationRepository;

    @Autowired
    private MiddlewareRepository middlewareRepository;

    @Autowired
    private RateLimitRepository ratelimitRepository;

    @Autowired
    private AppRepository appRepository;

    @Autowired
    private AMQPInterceptorService amqpInterceptorService;

    @Value("${zuul.filter.root}")
    private String zuulFilterRoot;

    /**
     * Finds a {@link Interceptor} by its ID.
     *
     * @param id The Id of the {@link Interceptor}
     * @return The {@link Interceptor} found
     */
    @Transactional(readOnly = true)
    public Interceptor find(Long id) {

        Interceptor interceptor = interceptorRepository.findOne(id);
        HeimdallException.checkThrow(isBlank(interceptor), GLOBAL_RESOURCE_NOT_FOUND);

        return interceptor;
    }

    /**
     * Generates a paged list of {@link Interceptor} from a request.
     *
     * @param interceptorDTO The {@link InterceptorDTO}
     * @param pageableDTO    The {@link PageableDTO}
     * @return The paged {@link Interceptor} list as a {@link InterceptorPage} object
     */
    @Transactional(readOnly = true)
    public InterceptorPage list(InterceptorDTO interceptorDTO, PageableDTO pageableDTO) {

        Interceptor interceptor = GenericConverter.mapper(interceptorDTO, Interceptor.class);

        Example<Interceptor> example = Example.of(interceptor, ExampleMatcher.matching().withIgnoreCase().withStringMatcher(StringMatcher.CONTAINING));

        Pageable pageable = Pageable.setPageable(pageableDTO.getOffset(), pageableDTO.getLimit());
        Page<Interceptor> page = interceptorRepository.findAll(example, pageable);

        return new InterceptorPage(PageDTO.build(page));
    }

    /**
     * Generates a list of {@link Interceptor} from a request.
     *
     * @param interceptorDTO The {@link InterceptorDTO}
     * @return The List<{@link Interceptor}> list
     */
    @Transactional(readOnly = true)
    public List<Interceptor> list(InterceptorDTO interceptorDTO) {

        Interceptor interceptor = GenericConverter.mapper(interceptorDTO, Interceptor.class);

        Example<Interceptor> example = Example.of(interceptor, ExampleMatcher.matching().withIgnoreCase().withStringMatcher(StringMatcher.CONTAINING));

        return interceptorRepository.findAll(example);
    }

    /**
     * Saves a {@link Interceptor} to the repository.
     *
     * @param interceptorDTO The {@link InterceptorDTO}
     * @return The {@link Interceptor} saved
     */
    @Transactional
    public Interceptor save(InterceptorDTO interceptorDTO) {

        Interceptor interceptor = GenericConverter.mapper(interceptorDTO, Interceptor.class);

        interceptor = validateLifeCycle(interceptor);

        validateTemplate(interceptor.getType(), interceptor.getContent());

        List<Long> ignoredResources = ignoredValidate(interceptorDTO.getIgnoredResources(), resourceRepository);
        HeimdallException.checkThrow(Objeto.notBlank(ignoredResources), INTERCEPTOR_IGNORED_INVALID, ignoredResources.toString());

        List<Long> ignoredOperations = ignoredValidate(interceptorDTO.getIgnoredOperations(), operationRepository);
        HeimdallException.checkThrow(Objeto.notBlank(ignoredOperations), INTERCEPTOR_IGNORED_INVALID, ignoredOperations.toString());

        if (TypeInterceptor.RATTING == interceptor.getType()) {
            mountRatelimitInRedis(interceptor);
        }

        if (TypeInterceptor.OAUTH == interceptor.getType()) {
            checkAppAndClientId(interceptor);
        }

        interceptor = interceptorRepository.save(interceptor);

        if (TypeInterceptor.MIDDLEWARE.equals(interceptor.getType())) {

            Operation operation = operationRepository.findOne(interceptor.getReferenceId());
            if (Objeto.notBlank(operation)) {
                Api api = operation.getResource().getApi();

                List<Middleware> middlewares = middlewareRepository.findByStatusAndApiId(Status.ACTIVE, api.getId());
                for (Middleware middleware : middlewares) {

                    List<Interceptor> interceptors = middleware.getInterceptors();
                    if (Objeto.notBlank(interceptors)) {

                        interceptors.addAll(Lists.newArrayList(interceptor));
                        middleware.setInterceptors(interceptors);
                    } else {

                        interceptors = Lists.newArrayList(interceptor);
                        middleware.setInterceptors(interceptors);
                    }
                }

                middlewareRepository.save(middlewares);
            }
        }

        amqpInterceptorService.dispatchInterceptor(interceptor.getId());

        return interceptor;
    }

    private Object validateTemplate(TypeInterceptor type, String content) {
        return type.getHeimdallInterceptor().parseContent(content);
    }

    /**
     * Updates a {@link Interceptor} by its ID.
     *
     * @param id             The ID of the {@link Interceptor} to be updated
     * @param interceptorDTO The {@link InterceptorDTO}
     * @return The updated {@link Interceptor}
     */
    public Interceptor update(Long id, InterceptorDTO interceptorDTO) {

        Interceptor interceptor = interceptorRepository.findOne(id);
        HeimdallException.checkThrow(isBlank(interceptor), GLOBAL_RESOURCE_NOT_FOUND);
        interceptor = GenericConverter.mapperWithMapping(interceptorDTO, interceptor, new InterceptorMap());

        interceptor = validateLifeCycle(interceptor);

        if (TypeInterceptor.RATTING == interceptor.getType()) {
            mountRatelimitInRedis(interceptor);
        }

        if (TypeInterceptor.OAUTH == interceptor.getType()) {
            checkAppAndClientId(interceptor);
        }

        interceptor = interceptorRepository.save(interceptor);
        amqpInterceptorService.dispatchInterceptor(interceptor.getId());

        return interceptor;
    }

    /**
     * Deletes a{@link Interceptor} by its ID.
     *
     * @param id The Id of the {@link Interceptor} to be deleted
     */
    @Transactional
    public void delete(Long id) {

        Interceptor interceptor = interceptorRepository.findOne(id);
        HeimdallException.checkThrow(isBlank(interceptor), GLOBAL_RESOURCE_NOT_FOUND);

        String fileName = StringUtils.concatCamelCase(interceptor.getLifeCycle().name(), interceptor.getType().name(), interceptor.getExecutionPoint().getFilterType(), interceptor.getId().toString()) + ".groovy";
        String pathName = String.join(File.separator, zuulFilterRoot, interceptor.getExecutionPoint().getFilterType(), fileName);

        if (TypeInterceptor.RATTING == interceptor.getType()) {

            String path = createPath(interceptor);

            ratelimitRepository.delete(path);
        }

        if (TypeInterceptor.MIDDLEWARE.equals(interceptor.getType())) {

            String api = interceptor.getOperation().getResource().getApi().getId().toString();
            pathName = String.join("/", zuulFilterRoot, MIDDLEWARE_API_ROOT, api, fileName);
        }

        interceptorRepository.delete(interceptor);
        amqpInterceptorService.dispatchRemoveInterceptors(new InterceptorFileDTO(interceptor.getId(), pathName));
    }

    protected void mountRatelimitInRedis(Interceptor interceptor) {

        RateLimitDTO rateLimitDTO = new RateLimitDTO();
        try {
            rateLimitDTO = JsonUtils.convertJsonToObject(interceptor.getContent(), RateLimitDTO.class);
        } catch (Exception e) {

            log.error(e.getMessage(), e);
            ExceptionMessage.INTERCEPTOR_INVALID_CONTENT.raise(interceptor.getType().name(), TemplateUtils.TEMPLATE_RATTING);
        }

        String path = createPath(interceptor);

        RateLimit rate = new RateLimit(path, rateLimitDTO.getCalls(), rateLimitDTO.getInterval());
        ratelimitRepository.save(rate);
    }

    /**
     * Private method to validate the lifecycle of a {@link Interceptor}.<br/>
     * Life cycle can be a PLAN, a RESOURCE or a OPERATION.
     *
     * @param interceptor The {@link Interceptor} to be validated
     * @return The validated {@link Interceptor}
     */
    private Interceptor validateLifeCycle(Interceptor interceptor) {

        switch (interceptor.getLifeCycle()) {
            case PLAN:
                Plan plan = planRepository.findOne(interceptor.getReferenceId());
                HeimdallException.checkThrow(isBlank(plan), INTERCEPTOR_REFERENCE_NOT_FOUND);
                interceptor.setResource(null);
                interceptor.setOperation(null);
                interceptor.setPlan(plan);
                interceptor.setApi(plan.getApi());
                break;
            case RESOURCE:
                Resource resource = resourceRepository.findOne(interceptor.getReferenceId());
                HeimdallException.checkThrow(isBlank(resource), INTERCEPTOR_REFERENCE_NOT_FOUND);
                interceptor.setOperation(null);
                interceptor.setPlan(null);
                interceptor.setResource(resource);
                interceptor.setApi(resource.getApi());
                break;
            case OPERATION:
                Operation operation = operationRepository.findOne(interceptor.getReferenceId());
                HeimdallException.checkThrow(isBlank(operation), INTERCEPTOR_REFERENCE_NOT_FOUND);
                interceptor.setResource(null);
                interceptor.setPlan(null);
                interceptor.setOperation(operation);
                interceptor.setApi(operation.getResource().getApi());
                break;
            default:
                break;
        }

        return interceptor;
    }

    private List<Long> ignoredValidate(List<ReferenceIdDTO> referenceIdDTOs, JpaRepository<?, Long> repository) {

        List<Long> invalids = Lists.newArrayList();
        if (Objeto.notBlank(referenceIdDTOs)) {

            for (ReferenceIdDTO ignored : referenceIdDTOs) {

                Object o = repository.findOne(ignored.getId());
                if (Objeto.isBlank(o)) {
                    invalids.add(ignored.getId());
                }
            }
        }

        return invalids;
    }

    /*
     * Creates the path to be used as key for the RateLimit repository
     */
    private String createPath(Interceptor interceptor) {

        if (InterceptorLifeCycle.PLAN == interceptor.getLifeCycle()) {

            Plan plan = planRepository.findOne(interceptor.getReferenceId());
            return plan.getApi().getBasePath();

        } else if (InterceptorLifeCycle.RESOURCE == interceptor.getLifeCycle()) {

            Resource res = resourceRepository.findOne(interceptor.getReferenceId());
            return res.getApi().getBasePath() + "-" + res.getName();

        } else {

            Operation op = operationRepository.findOne(interceptor.getReferenceId());
            return op.getResource().getApi().getBasePath() + "-" + op.getResource().getName() + "-" + op.getPath();
        }
    }

    /*
     * When adding a OAuth Interceptor a privateKey is expected.
     * This privateKey must be the ClientId from an App that is related to the Api via a Plan,
     * if not throws an Exception.
     */
    private void checkAppAndClientId(Interceptor interceptor) {
        try {
            String content = interceptor.getContent();
            OAuthDTO oAuthDTO = JsonUtils.convertJsonToObject(content, OAuthDTO.class);

            final String privateKey = oAuthDTO.getPrivateKey();
            App app = appRepository.findByClientId(privateKey);

            HeimdallException.checkThrow(app == null, APP_NOT_EXIST);

            final List<Plan> appPlans = app.getPlans();
            final List<Plan> apiPlans = interceptor.getApi().getPlans();

            Set<Plan> common = new HashSet<>(appPlans);
            common.retainAll(apiPlans);

            HeimdallException.checkThrow(common.size() == 0, INTERCEPTOR_NO_APP_FOUND);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            ExceptionMessage.INTERCEPTOR_INVALID_CONTENT.raise(TypeInterceptor.OAUTH.name(), TemplateUtils.TEMPLATE_OAUTH);
        }
    }
}
