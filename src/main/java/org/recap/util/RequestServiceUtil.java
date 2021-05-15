package org.recap.util;

import org.apache.commons.lang3.StringUtils;
import org.recap.ScsbConstants;
import org.recap.model.jpa.CollectionGroupEntity;
import org.recap.model.jpa.InstitutionEntity;
import org.recap.model.jpa.RequestItemEntity;
import org.recap.model.jpa.RequestTypeEntity;
import org.recap.model.reports.TransactionReport;
import org.recap.model.reports.TransactionReports;
import org.recap.model.search.RequestForm;
import org.recap.repository.jpa.CollectionGroupDetailsRepository;
import org.recap.repository.jpa.ImsLocationDetailRepository;
import org.recap.repository.jpa.InstitutionDetailsRepository;
import org.recap.repository.jpa.RequestItemDetailsRepository;
import org.recap.repository.jpa.RequestTypeDetailsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManagerFactory;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Created by rajeshbabuk on 29/10/16.
 */
@Service
public class RequestServiceUtil {

    @Autowired
    private RequestItemDetailsRepository requestItemDetailsRepository;

    @Autowired
    private InstitutionDetailsRepository institutionDetailsRepository;

    @Autowired
    private CollectionGroupDetailsRepository collectionGroupDetailsRepository;

    @Autowired
    private ImsLocationDetailRepository imsLocationDetailRepository;

    @Autowired
    private RequestTypeDetailsRepository requestTypeDetailsRepository;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Value("${scsb.support.institution}")
    private String supportInstitution;

    /**
     * Based on the given search criteria in the request search UI page, this method builds the request search results to show them as rows in the request search UI page.
     *
     * @param requestForm the request form
     * @return the page
     */
    public Page<RequestItemEntity> searchRequests(RequestForm requestForm) {
        String patronBarcode = StringUtils.isNotBlank(requestForm.getPatronBarcode()) ? requestForm.getPatronBarcode().trim() : requestForm.getPatronBarcode();
        String itemBarcode = StringUtils.isNotBlank(requestForm.getItemBarcode()) ? requestForm.getItemBarcode().trim() : requestForm.getItemBarcode();
        String status = StringUtils.isNotBlank(requestForm.getStatus()) ? requestForm.getStatus().trim() : requestForm.getStatus();
        String institution = StringUtils.isNotBlank(requestForm.getInstitution()) ? requestForm.getInstitution().trim() : requestForm.getInstitution();
        InstitutionEntity institutionEntity = institutionDetailsRepository.findByInstitutionCode(institution);
        Integer imsLocationId = imsLocationDetailRepository.findByImsLocationCode(requestForm.getStorageLocation()).getId();
        Optional<InstitutionEntity> institutionEntityOptional = Optional.ofNullable(institutionEntity);
        if (!institutionEntityOptional.isPresent()) {
            institutionEntity = new InstitutionEntity();
            institutionEntity.setId(0);
        }
        Pageable pageable = PageRequest.of(requestForm.getPageNumber(), requestForm.getPageSize(), Sort.Direction.DESC, "id");
        Page<RequestItemEntity> requestItemEntities = null;
        requestItemEntities = (status.equals(ScsbConstants.SEARCH_REQUEST_ACTIVE)) ? requestItemDetailsRepository.findByPatronBarcodeAndItemBarcodeAndActiveAndInstitution(pageable, patronBarcode, itemBarcode,imsLocationId, institutionEntity.getId()) : requestItemDetailsRepository.findByPatronBarcodeAndItemBarcodeAndStatusAndInstitution(pageable, patronBarcode, itemBarcode, status,imsLocationId, institutionEntity.getId());

        return requestItemEntities;
    }

    /**
     * @return page requestItemEntities
     */
    public List<RequestItemEntity> exportExceptionReports(String institutionCode, Date fromDate, Date toDate) {
        InstitutionEntity institutionEntity = institutionDetailsRepository.findByInstitutionCode(institutionCode);
        List<RequestItemEntity> requestItemEntities = requestItemDetailsRepository.findByStatusAndInstitutionAndAll(ScsbConstants.REPORTS_EXCEPTION, institutionEntity.getId(), fromDate, toDate);
        return requestItemEntities;
    }

    /**
     * @return page requestItemEntities
     */
    public Page<RequestItemEntity> exportExceptionReportsWithDate(String institutionCode, Date fromDate, Date toDate, Integer pageNumber, Integer size) throws ParseException {
        Pageable pageable = PageRequest.of(pageNumber, size, Sort.Direction.DESC, ScsbConstants.ID);
        InstitutionEntity institutionEntity = institutionDetailsRepository.findByInstitutionCode(institutionCode);
        Page<RequestItemEntity> requestItemEntities = requestItemDetailsRepository.findByStatusAndInstitutionAndDateRange(pageable, ScsbConstants.REPORTS_EXCEPTION, institutionEntity.getId(), fromDate, toDate);
        return requestItemEntities;
    }
    /**
     *
     * @param fromDate
     * @param toDate
     * @return list of Transaction Reports
     */
    public List<TransactionReport> getTransactionReportCount(TransactionReports transactionReports, Date fromDate, Date toDate) {
        List<TransactionReport> transactionReportsList = new ArrayList<>();
        Map<Integer, String> institutionList = mappingInstitution();
        List<CollectionGroupEntity> collectionGroupEntities = collectionGroupDetailsRepository.findAll();
        Map<Integer, String> cgdCodes = pullCGDCodes(collectionGroupEntities);
        List<Object[]> list = requestItemDetailsRepository.pullTransactionReportCount(getKeysByValues(transactionReports.getOwningInsts(),institutionList), getKeysByValues(transactionReports.getRequestingInsts(),institutionList), getKeysByValues(transactionReports.getTypeOfUses(),getRequestTypes()), fromDate, toDate);
        for (Object[] o : list) {
            transactionReportsList.add(new TransactionReport(o[0].toString(), institutionList.get(Integer.parseInt(o[1].toString())), institutionList.get(Integer.parseInt(o[2].toString())), cgdCodes.get(Integer.parseInt(o[3].toString())), Long.parseLong(o[4].toString())));
        }
        return transactionReportsList;
    }

    /**
     *
     * @param fromDate
     * @param toDate
     * @return list of Transaction Reports
     */
    public List<TransactionReport> getTransactionReports(TransactionReports transactionReports,Date fromDate, Date toDate) {
        List<TransactionReport> transactionReportsList = new ArrayList<>();
        Pageable pageable = PageRequest.of(transactionReports.getPageNumber(), transactionReports.getPageSize());
        Map<Integer, String> institutionList = mappingInstitution();
        List<CollectionGroupEntity> collectionGroupEntities = collectionGroupDetailsRepository.findAll();
        Map<Integer, String> cgdCodes = pullCGDCodes(collectionGroupEntities);
        List<Object[]> reportsList = requestItemDetailsRepository.findTransactionReportsByOwnAndReqInstWithStatus(pageable, getKeysByValues(transactionReports.getOwningInsts(),institutionList), getKeysByValues(transactionReports.getRequestingInsts(),institutionList), getKeysByValues(transactionReports.getTypeOfUses(),getRequestTypes()), fromDate, toDate, getCGDIds(cgdCodes,transactionReports.getCgdType()));
        for (Object[] o : reportsList) {
            transactionReportsList.add(new TransactionReport(o[0].toString(), institutionList.get(Integer.parseInt(o[1].toString())), institutionList.get(Integer.parseInt(o[2].toString())), cgdCodes.get(Integer.parseInt(o[3].toString())), o[4].toString(), o[5].toString(), o[6].toString()));
        }
        return transactionReportsList;
    }

    /**
     *
     * @param transactionReports
     * @param fromDate
     * @param toDate
     * @return th elist of Transaction Reports
     */
    public List<TransactionReport> getTransactionReportsExport(TransactionReports transactionReports,Date fromDate, Date toDate) {
        List<TransactionReport> transactionReportsList = new ArrayList<>();
        Map<Integer, String> institutionList = mappingInstitution();
        List<CollectionGroupEntity> collectionGroupEntities = collectionGroupDetailsRepository.findAll();
        Map<Integer, String> cgdCodes = pullCGDCodes(collectionGroupEntities);
        List<Object[]> reportsList = requestItemDetailsRepository.findTransactionReportsByOwnAndReqInstWithStatusExport(getKeysByValues(transactionReports.getOwningInsts(),institutionList), getKeysByValues(transactionReports.getRequestingInsts(),institutionList), getKeysByValues(transactionReports.getTypeOfUses(),getRequestTypes()), fromDate, toDate, getCGDIds(cgdCodes,transactionReports.getCgdType()));
        for (Object[] o : reportsList) {
            transactionReportsList.add(new TransactionReport(o[0].toString(), institutionList.get(Integer.parseInt(o[1].toString())), institutionList.get(Integer.parseInt(o[2].toString())), cgdCodes.get(Integer.parseInt(o[3].toString())), o[4].toString(), o[5].toString(), o[6].toString()));
        }
        return transactionReportsList;
    }
    private Map<Integer, String> mappingInstitution() {
        Map<Integer, String> institutionList = new HashMap<>();
        List<InstitutionEntity> institutionEntities = institutionDetailsRepository.getInstitutionCodes(supportInstitution);
        institutionEntities.stream().forEach(inst -> institutionList.put(inst.getId(), inst.getInstitutionCode()));
        return institutionList;
    }
    private Map<Integer, String> pullCGDCodes(List<CollectionGroupEntity> collectionGroupEntities) {
        Map<Integer, String> cgdCodes = new HashMap<>();
        for (CollectionGroupEntity collectionGroupEntity : collectionGroupEntities){
            cgdCodes.put(collectionGroupEntity.getId(),collectionGroupEntity.getCollectionGroupCode());
        }
        return cgdCodes;
    }
    private List<Integer> getKeysByValues(List<String> instCodes, Map<Integer, String> instList) {
        List<Integer> instIds = new ArrayList<>();
            for (Map.Entry<Integer, String> entry : instList.entrySet()) {
                if(instCodes.stream().anyMatch(entry.getValue()::equalsIgnoreCase))
                    instIds.add(entry.getKey());
            }
            return instIds;
    }

    private Map<Integer, String> getRequestTypes() {
        Map<Integer, String> requestTypes = new HashMap<>();
       List<RequestTypeEntity> requestTypeEntities = requestTypeDetailsRepository.findAll();
       for (RequestTypeEntity requestTypeEntity : requestTypeEntities){
           requestTypes.put(requestTypeEntity.getId(),requestTypeEntity.getRequestTypeCode());
       }
       return requestTypes;
    }

    private static Integer getCGDIds(Map<Integer, String> cgdCodes, String cgdType) {
        return cgdCodes.entrySet().stream().filter(entry -> cgdType.equals(entry.getValue())).map(Map.Entry::getKey).findFirst().get();
    }
}
