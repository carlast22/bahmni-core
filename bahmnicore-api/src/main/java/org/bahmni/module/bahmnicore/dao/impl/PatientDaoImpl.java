package org.bahmni.module.bahmnicore.dao.impl;

import org.apache.commons.lang3.StringUtils;
import org.bahmni.module.bahmnicore.contract.patient.response.PatientResponse;
import org.bahmni.module.bahmnicore.contract.patient.search.PatientSearchBuilder;
import org.bahmni.module.bahmnicore.dao.PatientDao;
import org.bahmni.module.bahmnicore.model.bahmniPatientProgram.ProgramAttributeType;
import org.hibernate.Query;
import org.hibernate.SQLQuery;
import org.hibernate.SessionFactory;
import org.hibernate.Session;
import org.hibernate.criterion.Restrictions;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PersonAddress;
import org.openmrs.RelationshipType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Repository
public class PatientDaoImpl implements PatientDao {

    private SessionFactory sessionFactory;

    @Autowired
    public PatientDaoImpl(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public List<PatientResponse> getPatients(String identifier, String name, String customAttribute,
                                             String addressFieldName, String addressFieldValue, Integer length,
                                             Integer offset, String[] customAttributeFields, String programAttributeFieldValue,
                                             String programAttributeFieldName, String[] addressSearchResultFields,
                                             String[] patientSearchResultFields, String loginLocationUuid, Boolean filterPatientsByLocation, Boolean filterOnAllIdentifiers) {

        validateSearchParams(customAttributeFields, programAttributeFieldName,addressFieldName);


        ProgramAttributeType programAttributeType = getProgramAttributeType(programAttributeFieldName);

        SQLQuery sqlQuery = new PatientSearchBuilder(sessionFactory)
                .withPatientName(name)
                .withPatientAddress(addressFieldName,addressFieldValue, addressSearchResultFields)
                .withPatientIdentifier(identifier, filterOnAllIdentifiers)
                .withPatientAttributes(customAttribute, getPersonAttributeIds(customAttributeFields), getPersonAttributeIds(patientSearchResultFields))
                .withProgramAttributes(programAttributeFieldValue, programAttributeType)
                .withLocation(loginLocationUuid, filterPatientsByLocation)
                .buildSqlQuery(length,offset);
        return sqlQuery.list();
    }

    private void validateSearchParams(String[] customAttributeFields, String programAttributeFieldName, String addressFieldName) {
        List<Integer> personAttributeIds = getPersonAttributeIds(customAttributeFields);
        if (customAttributeFields != null && personAttributeIds.size() != customAttributeFields.length) {
            throw new IllegalArgumentException(String.format("Invalid Attribute In Patient Attributes [%s]", StringUtils.join(customAttributeFields, ", ")));
        }

        ProgramAttributeType programAttributeTypeId = getProgramAttributeType(programAttributeFieldName);
        if (programAttributeFieldName != null && programAttributeTypeId == null) {
            throw new IllegalArgumentException(String.format("Invalid Program Attribute %s", programAttributeFieldName));
        }


        if(!isValidAddressField(addressFieldName)){
            throw new IllegalArgumentException(String.format("Invalid Address Filed %s", addressFieldName));
        }
    }

    private boolean isValidAddressField(String addressFieldName) {
        if(addressFieldName==null)return true;
        String query = "SELECT DISTINCT COLUMN_NAME FROM information_schema.columns WHERE\n" +
                "LOWER (TABLE_NAME) ='person_address' and LOWER(COLUMN_NAME) IN "+
                "( :personAddressField)";
        Query queryToGetAddressFields = sessionFactory.getCurrentSession().createSQLQuery( query);
        queryToGetAddressFields.setParameterList("personAddressField", Arrays.asList(addressFieldName.toLowerCase()));
        List list = queryToGetAddressFields.list();
        return list.size()>0;
    }

    private ProgramAttributeType getProgramAttributeType(String programAttributeField) {
        if(StringUtils.isEmpty(programAttributeField)){
            return null;
        }

        return (ProgramAttributeType) sessionFactory.getCurrentSession().createCriteria(ProgramAttributeType.class).
                add(Restrictions.eq("name",programAttributeField)).uniqueResult();
    }

    private List<Integer> getPersonAttributeIds(String[] patientAttributes) {
        if (patientAttributes == null || patientAttributes.length == 0 ){
            return new ArrayList<>();
        }

        String query = "select person_attribute_type_id from person_attribute_type where name in " +
                "( :personAttributeTypeNames)";
        Query queryToGetAttributeIds = sessionFactory.getCurrentSession().createSQLQuery( query);
        queryToGetAttributeIds.setParameterList("personAttributeTypeNames", Arrays.asList(patientAttributes));
        List list = queryToGetAttributeIds.list();
        return (List<Integer>) list;
    }

    @Override
    public Patient getPatient(String identifier) {
        Session currentSession = sessionFactory.getCurrentSession();
        List<PatientIdentifier> ident = currentSession.createQuery("from PatientIdentifier where identifier = :ident").setString("ident", identifier).list();
        if (!ident.isEmpty()) {
            return ident.get(0).getPatient();
        }
        return null;
    }

    @Override
    public List<Patient> getPatients(String patientIdentifier, boolean shouldMatchExactPatientId) {
        if (!shouldMatchExactPatientId) {
            String partialIdentifier = "%" + patientIdentifier;
            Query querytoGetPatients = sessionFactory.getCurrentSession().createQuery(
                    "select pi.patient " +
                            " from PatientIdentifier pi " +
                            " where pi.identifier like :partialIdentifier ");
            querytoGetPatients.setString("partialIdentifier",partialIdentifier);
            return querytoGetPatients.list();
        }

        Patient patient = getPatient(patientIdentifier);
        List<Patient> result = (patient == null ? new ArrayList<Patient>(): Arrays.asList(patient));
        return result;
    }

    @Override
    public List<RelationshipType> getByAIsToB(String aIsToB) {
        Query querytoGetPatients = sessionFactory.getCurrentSession().createQuery(
                "select rt " +
                        " from RelationshipType rt " +
                        " where rt.aIsToB = :aIsToB ");
        querytoGetPatients.setString("aIsToB", aIsToB);
        return querytoGetPatients.list();
    }
}
