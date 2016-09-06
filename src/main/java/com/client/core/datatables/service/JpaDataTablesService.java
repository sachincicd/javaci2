package com.client.core.datatables.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.persistence.EntityNotFoundException;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.Validator;

import com.client.core.AppContext;
import com.client.core.base.dao.GenericDao;
import com.client.core.base.model.jpa.JpaEntity;
import com.client.core.base.tools.data.QueryResult;
import com.client.core.base.tools.query.QueryHelper;
import com.client.core.datatables.model.configuration.column.ColumnConfiguration;
import com.client.core.datatables.tools.JQueryDataTableParamModel;
import com.google.common.collect.Maps;

/**
 * Extend this class to get data tables editable functionality for free.
 * 
 * Contains generic logic to handle datatables retrieving of data, editing data, entity validation, sorting of rows, filtering of rows.
 * 
 * If custom functionality needs to be added, DO NOT edit this class, instead overwrite the relevant methods in your concrete class.
 * 
 * @author magnus.palm
 * 
 * @param <T>
 *            entity that will be handled by the datatables, represents one entity of any type, such as JPA MongoDB etc.
 * @param <ID>
 *            the unique identifier of entity T
 */
public abstract class JpaDataTablesService<T extends JpaEntity<ID>, ID> extends AbstractDataTablesService<T, ID> {

	private final Logger log = Logger.getLogger(getClass());

	private final GenericDao<T, ID> genericDao;
    private final QueryHelper queryHelper;

    public JpaDataTablesService(Validator validator, GenericDao<T, ID> genericDao) {
        super(validator);
        this.genericDao = genericDao;
        this.queryHelper = AppContext.getApplicationContext().getBean(QueryHelper.class);
    }

    public JpaDataTablesService(GenericDao<T, ID> genericDao) {
        this.genericDao = genericDao;
        this.queryHelper = AppContext.getApplicationContext().getBean(QueryHelper.class);
    }

    public JpaDataTablesService(Validator validator, GenericDao<T, ID> genericDao, QueryHelper queryHelper) {
        super(validator);
        this.genericDao = genericDao;
        this.queryHelper = queryHelper;
    }

    public JpaDataTablesService(GenericDao<T, ID> genericDao, QueryHelper queryHelper) {
        this.genericDao = genericDao;
        this.queryHelper = queryHelper;
    }

    protected Map<String, Object> getParameters(HttpServletRequest request) {
        return Maps.newLinkedHashMap();
    }

    protected String getQuery() {
        return new StringBuilder("SELECT a FROM ").append(genericDao.getType().getSimpleName()).append(" a").toString();
    }

    @Override
    @Transactional(readOnly = true)
    public T find(ID id) throws EntityNotFoundException {
        return genericDao.find(id);
    }

    @Override
    @Transactional
    protected T add(T transientObject) {
        return genericDao.merge(transientObject);
    }

    @Override
    @Transactional
    protected T update(T transientObject) {
        return genericDao.merge(transientObject);
    }

    @Override
    @Transactional
    protected void remove(T persistentObject) throws EntityNotFoundException {
        genericDao.remove(persistentObject);
    }

    @Override
    @Transactional(readOnly = true)
    protected List<T> getData(HttpServletRequest request, JQueryDataTableParamModel param) {
        Long totalRecords = genericDao.getCount(getQuery(), getParameters(request));

        param.setiTotalRecords(totalRecords.intValue());

        log.info(getQueryWithSortAndFilter(param));

        QueryResult<T> result = genericDao.query(getQueryWithSortAndFilter(param), getParameters(request), param.getiDisplayStart(), param.getiDisplayLength());

        param.setiTotalDisplayRecords(result.getTotal().intValue());

        return result.getData();
    }

    private String getQueryWithSortAndFilter(JQueryDataTableParamModel param) {
        String query = getQuery();

        return new StringBuilder(query).append(getFilter(query, param)).append(getSort(param)).toString();
    }

    private String getSort(JQueryDataTableParamModel param) {
        Integer sortIndex = param.getiSortColumnIndex();
        String sortDirection = param.getsSortDirection().equals("asc") ? "ASC" : "DESC";

        String field = getStandardColumnConfiguration().getColumn(sortIndex).getFieldName();

        return new StringBuilder(" ORDER BY ").append(field).append(" ").append(sortDirection).toString();
    }

    private String getFilter(String query, JQueryDataTableParamModel param) {
        if(StringUtils.isBlank(param.getsSearch())) {
            return "";
        }

        List<String> terms = getStandardColumnConfiguration().getColumnConfigMap().entrySet().parallelStream().filter( entry -> {
            return entry.getValue().isSearchable();
        }).map( entry -> {
            return new StringBuilder(entry.getValue().getFieldName()).append(" LIKE '%").append(param.getsSearch()).append("%'").toString();
        }).collect(Collectors.toList());

        if(terms.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();

        if(StringUtils.containsIgnoreCase(query, "where")) {
            result.append(" AND (");
        } else {
            result.append(" WHERE (");
        }

        result.append(terms.stream().collect(Collectors.joining(" OR ")));

        return result.append(")").toString();
    }

    private ColumnConfiguration columnConfiguration;

    private synchronized ColumnConfiguration getStandardColumnConfiguration() {
        if(this.columnConfiguration == null) {
            try {
                this.columnConfiguration = convertEntityToColumns(genericDao.getType().newInstance());
            } catch(NullPointerException | InstantiationException | IllegalAccessException e) {
                getLog().error("Error getting a standard instance of ColumnConfiguration.  The convertEntityToColumns method must be able to handle a brand new instance of your table entity.  A default constructor is also required.");
            }
        }

        return this.columnConfiguration;
    }

    protected String getQueryUsingKey(String key) {
        return queryHelper.getQueryUsingKey(key);
    }

}