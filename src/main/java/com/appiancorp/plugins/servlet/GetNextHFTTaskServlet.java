package com.appiancorp.plugins.servlet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import com.appiancorp.ps.sail.expr.PortalReport2SAIL;
import com.appiancorp.ps.sail.type.PortalReport;
import com.appiancorp.ps.sail.type.PortalReportCell;
import com.appiancorp.ps.sail.type.PortalReportColumn;
import com.appiancorp.ps.sail.type.PortalReportColumnData;
import com.appiancorp.ps.sail.type.PortalReportFilter;
import com.appiancorp.services.ServiceContext;
import com.appiancorp.services.WebServiceContextFactory;
import com.appiancorp.suiteapi.common.Constants;
import com.appiancorp.suiteapi.common.ServiceLocator;
import com.appiancorp.suiteapi.common.exceptions.ExpressionException;
import com.appiancorp.suiteapi.common.exceptions.InvalidOperationException;
import com.appiancorp.suiteapi.common.exceptions.InvalidStateException;
import com.appiancorp.suiteapi.common.exceptions.InvalidUserException;
import com.appiancorp.suiteapi.common.exceptions.PrivilegeException;
import com.appiancorp.suiteapi.process.Assignment;
import com.appiancorp.suiteapi.process.Assignment.Assignee;
import com.appiancorp.suiteapi.process.ProcessDesignService;
import com.appiancorp.suiteapi.process.ProcessExecutionService;
import com.appiancorp.suiteapi.process.analytics2.Column;
import com.appiancorp.suiteapi.process.analytics2.ProcessAnalyticsService;
import com.appiancorp.suiteapi.process.analytics2.ProcessReport;
import com.appiancorp.suiteapi.process.analytics2.ReportData;
import com.appiancorp.suiteapi.process.analytics2.ReportResultPage;
import com.appiancorp.suiteapi.process.exceptions.InvalidActivityException;
import com.appiancorp.suiteapi.process.exceptions.ReportComplexityException;
import com.appiancorp.suiteapi.process.exceptions.ReportSizeException;
import com.appiancorp.suiteapi.process.exceptions.UnsupportedReportSpecificationException;
import com.appiancorp.suiteapi.type.AppianType;
import com.appiancorp.suiteapi.type.Datatype;
import com.appiancorp.suiteapi.type.NamedTypedValue;
import com.appiancorp.suiteapi.type.TypeService;
import com.appiancorp.suiteapi.type.TypedValue;
import com.appiancorp.suiteapi.type.exceptions.InvalidTypeException;

/**
 * Grab next available task in the HFT_GET_ALL_TASKS_LIST report
 *
 * @author michael.chirlin
 */
public class GetNextHFTTaskServlet extends HttpServlet {

	private static final long serialVersionUID = 9169350625721783901L;
	private static final Logger LOG = Logger.getLogger(GetNextHFTTaskServlet.class);

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.sendRedirect(calculateUrl(request, response));
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException  {
		response.sendRedirect(calculateUrl(request, response));
	}

	private String calculateUrl(HttpServletRequest request, HttpServletResponse response) throws IOException {
		ServiceContext sc = WebServiceContextFactory.getServiceContext(request);
		Long reportId = (Long)evaluateExpression(sc,"cons!HFT_GET_ALL_TASKS_LIST");
		String user = request.getRemoteUser();
		
		try {
			// Loop through various sorting filters 
			
			String[] accessTypes = calculateAccessTypes(
					sc,
					user,
					new String[]{"cons!HFT_PRIVATE_ASSOCIATE_GROUP", "cons!HFT_ASSOCIATE_USERS_GROUP", "cons!HFT_REGULAR_USERS_GROUP"},
					new String[]{"Private", "Associate", "Regular"}
			);
			String[] heloTypes = {"HELON", "HELOC"};
			String[] statusTypes = calculateAccessTypes(
					sc,
					user,
					new String[]{"cons!HFT_SUPERVISORS_GROUP", "cons!HFT_USERS"},
					new String[]{"PENDING SUPERVISOR REVIEW", "NEW"}
			);
			
			for(String accessType : accessTypes) {
				for(String heloType : heloTypes) {
					for(String statusType : statusTypes) {
						PortalReportFilter[] filters = createFilters(sc, accessType, heloType, statusType);
						
						Long[] taskIds = getPortalReportDataSubset(sc, reportId, filters);
						for(Long taskId:taskIds) {
							if(assignTask(sc, taskId, user)) {
								return("/suite/tempo/tasks/task/" + String.valueOf(taskId));
							}
						}
					}
				}
			}
			return("/suite/tempo/tasks/assignedtome");
		} catch (Exception e) {
			return("/suite/tempo/tasks/assignedtome");
		}
	}


	public Object evaluateExpression(ServiceContext sc, String expression) {    
		TypedValue tv;
		try {
			ProcessDesignService pds = ServiceLocator.getProcessDesignService(sc);
			tv = pds.evaluateExpression(expression);

			TypeService ts = ServiceLocator.getTypeService(sc);
			Datatype type = ts.getType(tv.getInstanceType());

			return build(ts, type, tv.getValue());

		} catch (Exception e) {
			LOG.error("An error occurred while trying to test the rule: ", e);
		}

		return null;

	}

	private Boolean assignTask(ServiceContext sc, Long taskId, String user) 
			throws InvalidOperationException, InvalidActivityException, PrivilegeException, InvalidStateException, InvalidUserException {
		ProcessExecutionService pes = ServiceLocator.getProcessExecutionService(sc);

		Long[] taskIds = {taskId};

		Assignee[] currentAssignees = pes.getAssigneePoolForTasks(taskIds);

		for(Assignee assignee:currentAssignees) {
			if(assignee.getType() != Assignment.ASSIGNEE_TYPE_GROUPS) {
				return false;
			}
		}

		Assignee assignee = new Assignee();
		assignee.setValue(user);
		assignee.setType(new Long(Assignment.ASSIGNEE_TYPE_USERS));
		assignee.setPrivilege(Assignment.PRIVILEGE_REASSIGN_TO_ANY);
		Assignee[] assignees = {assignee};
		pes.reassignTask(taskId, assignees);

		return true;
	}

	private Object build(TypeService ts, Datatype type, Object o)
			throws InvalidTypeException {

		if (type.getList() == null) {
			JSONArray a = new JSONArray();
			for (int i = 0; i < ((Object[]) o).length; i++) {
				a.put(i,build(ts, ts.getType(type.getTypeof()), ((Object[]) o)[i]));
			}
			return a;
		} else {
			if (type.isRecordType()) {
				JSONObject jsonKvp = new JSONObject();
				NamedTypedValue[] ntv = type.getInstanceProperties();
				for (int i = 0; i < ntv.length; i++) {
					String attr = ntv[i].getName();
					Datatype subtype = ts.getType(ntv[i].getInstanceType());
					Object val = ((Object[]) o)[i];
					if (val instanceof Object[]) {
						jsonKvp.put(attr, build(ts, subtype, val));
					} else {
						if (val instanceof java.sql.Date) {
							val = ((java.sql.Date)val).getTime();
						} else if (val instanceof java.util.Date) {
							val = ((java.util.Date)val).getTime();
						} else if (val instanceof java.sql.Timestamp) {
							val = ((java.sql.Timestamp)val).getTime();
						}
						jsonKvp.put(attr, val);
					}
				}
				return jsonKvp;
			} else {
				return o;
			}
		}
	}

	private Long[] getPortalReportDataSubset(ServiceContext sc, Long reportId, PortalReportFilter[] filters)
			throws PrivilegeException, UnsupportedReportSpecificationException, ReportComplexityException, ReportSizeException, ExpressionException {

		ProcessAnalyticsService pas = ServiceLocator.getProcessAnalyticsService2(sc);
		PortalReport2SAIL p2s = new PortalReport2SAIL();
		ProcessReport processReport = pas.getProcessReport(reportId);

		ReportData reportData = p2s.getReportDataWithContext(sc, processReport, null, filters);

		reportData.setStartIndex(0);
		reportData.setBatchSize(25); 		// Can increase this if more than 25 people are clicking at the same time
		reportData.setSortColumnLocalId(16); // Sorting by Column 17 (Funding Date)
		reportData.setSortOrder(Constants.SORT_ORDER_ASCENDING);

		ReportResultPage rp = pas.getReportPageWithTypedValues(reportData);

		return rp.getTaskIds();
		
	    /*PortalReport pReport = new PortalReport();
		List<PortalReportColumn> columnList = new ArrayList<>();
		List<PortalReportColumnData> dataList = new ArrayList<>();
		List<Long> identifierList = new ArrayList<>();

		// set portal report
		pReport.setName(processReport.getDisplay().getName());
		pReport.setDescription(processReport.getDisplay().getDescription());

		// set columns
		for (Column column : reportData.getColumns()) {
			if (column.getShow()) {
				PortalReportColumn pColumn = new PortalReportColumn();

				// unescape characters for the column label (e.g # & < >)
				pColumn.setLabel(StringEscapeUtils.unescapeHtml(column.getName()));
				pColumn.setField(column.getLocalId().toString());
				pColumn.setAlignment((column.getFormatToken().equals("number")) ? "RIGHT" : "LEFT");

				columnList.add(pColumn);
				dataList.add(new PortalReportColumnData());
			}
		}

		// set data
		for (HashMap<String, Object> map : (HashMap<String, Object>[]) rp.getResults()) {
			identifierList.add((Long) ((TypedValue) map.get("id")).getValue());

			for (int i=0; i<columnList.size(); i++) {
				PortalReportColumn pColumn = columnList.get(i);
				PortalReportColumnData pData = dataList.get(i);
				PortalReportCell cell = new PortalReportCell();

				cell.setValue((TypedValue) map.get("c" + pColumn.getField()));
				cell.setDrilldown((TypedValue) map.get("dp" + pColumn.getField()));

				pData.addCell(cell);
			}
		}

		return new PortalReportDataSubset(
				pagingInfo,
				(int) rp.getAvailableItems(),
				dataList,
				identifierList,
				pReport,
				columnList
				);*/
	  
	}
	
	private PortalReportFilter[] createFilters(ServiceContext sc, String accessType, String heloType, String statusType) {

		// Create Access Type Filter
		
		String field = "10";
		String compType = "EQUAL";
		Object value = accessType;
		TypedValue tv = new TypedValue(new Long(AppianType.STRING), value);
		PortalReportFilter accessTypeFilter = new PortalReportFilter(field, compType, tv);
		
		// Create HELO Type Filter
		
		field = "11";
		compType = "EQUAL";
		value = heloType;
		tv = new TypedValue(new Long(AppianType.STRING), value);
		PortalReportFilter heloTypeFilter = new PortalReportFilter(field, compType, tv);
		
		// Create Case Status Filter
		
		field = "14";
		compType = "EQUAL";
		value = statusType;
		tv = new TypedValue(new Long(AppianType.STRING), value);
		PortalReportFilter caseStatusFilter = new PortalReportFilter(field, compType, tv);
		
		PortalReportFilter[] filters = {accessTypeFilter, heloTypeFilter, caseStatusFilter};
		
		return filters;
	}
	
	private String[] calculateAccessTypes(ServiceContext sc, String user, String[] groups, String[] types) {
		
		List<String> accessTypes = new ArrayList<String>();
		
		for(int i = 0; i < groups.length; i++) {
			Object memberOfPrivate = evaluateExpression(sc, "fn!isusermemberofgroup(touser(\"" + user + "\"), " + groups[i] + ")");
			if((Long)memberOfPrivate == 1) {
				accessTypes.add(types[i]);
			}
		}
		
		return accessTypes.toArray(new String[]{});
	}
}
