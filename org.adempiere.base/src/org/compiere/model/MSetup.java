/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 1999-2006 ComPiere, Inc. All Rights Reserved.                *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * ComPiere, Inc., 2620 Augustine Dr. #245, Santa Clara, CA 95054, USA        *
 * or via info@compiere.org or http://www.compiere.org/license.html           *
 *****************************************************************************/
package org.compiere.model;

import static org.compiere.model.SystemIDs.COUNTRY_US;
import static org.compiere.model.SystemIDs.SCHEDULE_10_MINUTES;
import static org.compiere.model.SystemIDs.SCHEDULE_15_MINUTES;

import java.io.File;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Properties;
import java.util.logging.Level;

import org.adempiere.exceptions.AdempiereException;
import org.adempiere.util.ProcessUtil;
import org.compiere.process.ProcessInfo;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.util.AdempiereUserError;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.DisplayType;
import org.compiere.util.Env;
import org.compiere.util.KeyNamePair;
import org.compiere.util.Msg;
import org.compiere.util.Trx;

/**
 * Initial Setup Model
 *
 * @author Jorg Janke
 * @version $Id: MSetup.java,v 1.3 2006/07/30 00:51:02 jjanke Exp $
 * 
 * @author Teo Sarca, SC ARHIPAC SERVICE SRL
 *         <li>FR [ 1795384 ] Setup: create default accounts records is too
 *         rigid
 * @author Carlos Ruiz - globalqss
 *         <li>Setup correctly IsSOTrx for return documents
 */
public class MSetup {

	/**
	 * Constructor
	 * 
	 * @param ctx      context
	 * @param WindowNo window
	 */
	public MSetup(Properties ctx, int WindowNo, Trx trx) {
		m_ctx = new Properties(ctx); // copy
		m_lang = Env.getAD_Language(m_ctx);
		m_WindowNo = WindowNo;
		m_trx = trx;
		m_TrxName = trx.getTrxName();
	} // MSetup

	/** Logger */
	protected CLogger log = CLogger.getCLogger(getClass());

	protected Properties m_ctx;
	protected String m_lang;
	protected int m_WindowNo;
	protected StringBuffer m_info;
	//
	protected String m_clientName;
	//
	protected NaturalAccountMap<String, MElementValue> m_nap = null;
	//
	protected MClient m_client;
	protected MOrg m_org;
	protected MAcctSchema m_as;
	//
	protected MCalendar m_calendar;
	protected int m_AD_Tree_Account_ID;
	protected int C_Cycle_ID;
	//
	protected boolean m_hasProject = false;
	protected boolean m_hasMCampaign = false;
	protected boolean m_hasSRegion = false;
	protected boolean m_hasActivity = false;

	protected MUser clientUser = null;
	protected MUser adminClientUser = null;
	protected String m_TrxName = null;
	protected Trx m_trx = null;

	/**
	 * Create Client Info. - Client, Trees, Org, Role, User, User_Role
	 * 
	 * @param clientName           client name
	 * @param orgName              org name
	 * @param userClient           user id client
	 * @param userOrg              user id org
	 * @param isSetInitialPassword
	 * @return true if created
	 */
	public final void createClient(String clientName, String orgValue, String orgName, String userClient, String userOrg,
			String phone, String phone2, String fax, String eMail, String taxID, String adminEmail, String userEmail,
			boolean isSetInitialPassword) throws Exception {
		log.info(clientName);

		// info header
		m_info = new StringBuffer();
		// Standard columns
		String name = null;

		/**
		 * Create Client
		 */
		name = clientName;
		if (name == null || name.length() == 0)
			name = "newClient";
		m_clientName = name;
		m_client = new MClient(m_ctx, 0, true, getTrxName());
		m_client.setValue(m_clientName);
		m_client.setName(m_clientName);
		m_client.saveEx();

		int AD_Client_ID = m_client.getAD_Client_ID();
		Env.setContext(m_ctx, m_WindowNo, "AD_Client_ID", AD_Client_ID);
		Env.setContext(m_ctx, Env.AD_CLIENT_ID, AD_Client_ID);

		// Info - Client
		m_info.append(Msg.translate(m_lang, "AD_Client_ID")).append("=").append(name).append("\n");

		// Setup Sequences
		if (!MSequence.checkClientSequences(m_ctx, AD_Client_ID, getTrxName())) {
			String err = "Sequences NOT created";
			throw new AdempiereException(err);
		}

		// Trees and Client Info
		if (!m_client.setupClientInfo(m_lang)) {
			String err = "Tenant Info NOT created";
			throw new AdempiereException(err);
		}
		m_AD_Tree_Account_ID = m_client.getSetup_AD_Tree_Account_ID();

		/**
		 * Create Org
		 */
		name = orgName;
		if (name == null || name.length() == 0)
			name = "newOrg";
		if (orgValue == null || orgValue.length() == 0)
			orgValue = name;
		m_org = addOrganization(m_client, orgValue, name);
		// Info
		m_info.append(Msg.translate(m_lang, "AD_Org_ID")).append("=").append(name).append("\n");

		// Set Organization Phone, Phone2, Fax, EMail
		MOrgInfo orgInfo = addOrgInfo(getAD_Org_ID(),getTrxName());
		orgInfo.setPhone(phone);
		orgInfo.setPhone2(phone2);
		orgInfo.setFax(fax);
		orgInfo.setEMail(eMail);
		if (taxID != null && taxID.length() > 0) {
			orgInfo.setTaxID(taxID);
		}
		orgInfo.saveEx();
		
		Env.setContext(m_ctx, m_WindowNo, "AD_Org_ID", getAD_Org_ID());
		Env.setContext(m_ctx, Env.AD_ORG_ID, getAD_Org_ID());

		/**
		 * Create Roles - Admin - User
		 */
		name = m_clientName + " Admin";
		MRole adminRole = addRole(name, true, MRole.USERLEVEL_ClientPlusOrganization, MRole.PREFERENCETYPE_Client,
				true, getTrxName());
		// Info - Admin Role
		m_info.append(Msg.translate(m_lang, "AD_Role_ID")).append("=").append(name).append("\n");
		addOrgAccessRole(adminRole, 0);
		addOrgAccessRole(adminRole, m_org.getAD_Org_ID());
		//
		name = m_clientName + " User";
		MRole userRole = addRole(name, false, MRole.USERLEVEL_Organization, MRole.PREFERENCETYPE_Client, false,
				getTrxName());
		addOrgAccessRole(userRole, m_org.getAD_Org_ID());

		// Info - Client Role
		m_info.append(Msg.translate(m_lang, "AD_Role_ID")).append("=").append(name).append("\n");

		/**
		 * Create Users - Client - Org
		 */
		name = userClient;
		if (name == null || name.length() == 0)
			name = m_clientName + "Client";
		adminClientUser = addUser(AD_Client_ID, name, adminEmail, isSetInitialPassword, getTrxName());
		// Info
		m_info.append(Msg.translate(m_lang, "AD_User_ID")).append("=").append(adminClientUser.getName()).append("/")
				.append(adminClientUser.getName()).append("\n");
		name = userOrg;
		if (name == null || name.length() == 0)
			name = m_clientName + "Client";
		clientUser = addUser(AD_Client_ID, name, userEmail, isSetInitialPassword, getTrxName());
		// Info
		m_info.append(Msg.translate(m_lang, "AD_User_ID")).append("=").append(clientUser.getAD_User_ID()).append("/")
				.append(clientUser.getName()).append("\n");
		/**
		 * Create User-Role
		 */
		addUserRole(adminClientUser, adminRole, getTrxName());
		addUserRole(adminClientUser, userRole, getTrxName());
		// OrgUser - User
		addUserRole(clientUser, userRole, getTrxName());

		// Processors
		addProcessors(MAcctProcessor.class, adminClientUser);
		addProcessors(MRequestProcessor.class, adminClientUser);

		log.info("fini");
	} // createClient
	
	// preserving backward compatibility with swing client
	public void createAccounting(KeyNamePair currency, boolean hasProduct, boolean hasBPartner, boolean hasProject,
			boolean hasMCampaign, boolean hasSRegion, File AccountingFile) throws Exception {
		createAccounting(currency, hasProduct, hasBPartner, hasProject, hasMCampaign, hasSRegion, false, AccountingFile,
				false, false, true);
	}

	/**************************************************************************
	 * Create Accounting elements. - Calendar - Account Trees - Account Values -
	 * Accounting Schema - Default Accounts
	 *
	 * @param currency           currency
	 * @param hasProduct         has product segment
	 * @param hasBPartner        has bp segment
	 * @param hasProject         has project segment
	 * @param hasMCampaign       has campaign segment
	 * @param hasSRegion         has sales region segment
	 * @param hasActivity        has activity segment
	 * @param AccountingFile     file name of accounting file
	 * @param inactivateDefaults inactivate the default accounts after created
	 * @param useDefaultCoA      use the Default CoA (load and group summary
	 *                           account)
	 * @return true if created
	 */
	public void createAccounting(KeyNamePair currency, boolean hasProduct, boolean hasBPartner, boolean hasProject,
			boolean hasMCampaign, boolean hasSRegion, boolean hasActivity, File AccountingFile, boolean useDefaultCoA,
			boolean inactivateDefaults, boolean withDefaultDocuments) throws Exception {
		if (log.isLoggable(Level.INFO))
			log.info(m_client.toString());
		//
		m_hasProject = hasProject;
		m_hasMCampaign = hasMCampaign;
		m_hasSRegion = hasSRegion;
		m_hasActivity = hasActivity;

		// Standard variables
		m_info = new StringBuffer();
		String name = null;
		/**
		 * Create Calendar
		 */
		m_calendar = new MCalendar(m_client);
		m_calendar.saveEx();
		// Info
		m_info.append(Msg.translate(m_lang, "C_Calendar_ID")).append("=").append(m_calendar.getName()).append("\n");

		if (m_calendar.createYear(m_client.getLocale()) == null)
			log.log(Level.SEVERE, "Year NOT inserted");

		// Create Account Elements
		name = m_clientName + " " + Msg.translate(m_lang, "Account_ID");
		MElement element = new MElement(m_client, name, MElement.ELEMENTTYPE_Account, m_AD_Tree_Account_ID);
		element.saveEx();
		int C_Element_ID = element.getC_Element_ID();
		m_info.append(Msg.translate(m_lang, "C_Element_ID")).append("=").append(name).append("\n");

		// Create Account Values
		m_nap = new NaturalAccountMap<String, MElementValue>(m_ctx, getTrxName());
		String errMsg = m_nap.parseFile(AccountingFile);
		if (errMsg.length() != 0) {
			log.log(Level.SEVERE, errMsg);
			m_info.append(errMsg);
			throw new AdempiereException(errMsg);
		}
		if (m_nap.saveAccounts(getAD_Client_ID(), getAD_Org_ID(), C_Element_ID, !inactivateDefaults))
			m_info.append(Msg.translate(m_lang, "C_ElementValue_ID")).append(" # ").append(m_nap.size()).append("\n");
		else {
			String err = "Acct Element Values NOT inserted";
			log.log(Level.SEVERE, err);
			m_info.append(err);
			throw new AdempiereException(err);
		}

		int summary_ID = m_nap.getC_ElementValue_ID("SUMMARY");
		if (log.isLoggable(Level.FINE))
			log.fine("summary_ID=" + summary_ID);
		if (summary_ID > 0) {
			DB.executeUpdateEx("UPDATE AD_TreeNode SET Parent_ID=? WHERE AD_Tree_ID=? AND Node_ID!=?",
					new Object[] { summary_ID, m_AD_Tree_Account_ID, summary_ID }, getTrxName());
		}

		/**
		 * Create AccountingSchema
		 */
		m_as = new MAcctSchema(m_client, currency);
		m_as.saveEx();
		// Info
		m_info.append(Msg.translate(m_lang, "C_AcctSchema_ID")).append("=").append(m_as.getName()).append("\n");

		createClientAccounting(name, C_Element_ID, hasProduct, hasBPartner, hasProject, hasMCampaign, hasSRegion,
				hasActivity);

		// Create Defaults Accounts
		createAccountingRecord(X_C_AcctSchema_GL.Table_Name);
		createAccountingRecord(X_C_AcctSchema_Default.Table_Name);
		
		// Update ClientInfo
		String sql = "UPDATE AD_ClientInfo SET C_AcctSchema1_ID=?, C_Calendar_ID=? WHERE AD_Client_ID= ? ";
		updateRecordInfo(sql, new Object[] {m_as.getC_AcctSchema_ID(),m_calendar.getC_Calendar_ID(), m_client.getAD_Client_ID() }, getTrxName());
		
		if(withDefaultDocuments) 
			createDefaultsDocuments();
		//
		log.info("fini");
	}

	

	private void createClientAccounting(String name, int C_Element_ID, boolean hasProduct, boolean hasBPartner,
			boolean hasProject, boolean hasMCampaign, boolean hasSRegion, boolean hasActivity) throws Exception {
		/**
		 * Create AccountingSchema Elements (Structure)
		 */
		String sql2 = null;
		if (Env.isBaseLanguage(m_lang, "AD_Reference")) // Get ElementTypes & Name
			sql2 = "SELECT Value, Name FROM AD_Ref_List WHERE AD_Reference_ID=181";
		else
			sql2 = "SELECT l.Value, t.Name FROM AD_Ref_List l, AD_Ref_List_Trl t "
					+ "WHERE l.AD_Reference_ID=181 AND l.AD_Ref_List_ID=t.AD_Ref_List_ID" + " AND t.AD_Language="
					+ DB.TO_STRING(m_lang); // bug [ 1638421 ]
		PreparedStatement stmt = null;
		ResultSet rs = null;
		try {
			stmt = DB.prepareStatement(sql2, getTrxName());
			rs = stmt.executeQuery();
			while (rs.next()) {
				String ElementType = rs.getString(1);
				name = rs.getString(2);
				boolean IsMandatory = false;
				boolean IsBalanced = false;
				int SeqNo = 0;
				//
				if (ElementType.equals("OO")) {
					IsMandatory = true;
					IsBalanced = true;
					SeqNo = 10;
					addAcctSchemaElement(m_as.getC_AcctSchema_ID(), name, C_Element_ID, IsMandatory, IsBalanced, SeqNo,
							ElementType, getTrxName());
				} else if (ElementType.equals("AC")) {
					IsMandatory = true;
					SeqNo = 20;
					addAcctSchemaElement(m_as.getC_AcctSchema_ID(), name, C_Element_ID, IsMandatory, IsBalanced, SeqNo,
							ElementType, getTrxName());
				} else if (ElementType.equals("PR") && hasProduct) {
					IsMandatory = false;
					SeqNo = 30;
					addAcctSchemaElement(m_as.getC_AcctSchema_ID(), name, C_Element_ID, IsMandatory, IsBalanced, SeqNo,
							ElementType, getTrxName());
				} else if (ElementType.equals("BP") && hasBPartner) {
					IsMandatory = false;
					SeqNo = 40;
					addAcctSchemaElement(m_as.getC_AcctSchema_ID(), name, C_Element_ID, IsMandatory, IsBalanced, SeqNo,
							ElementType, getTrxName());
				} else if (ElementType.equals("PJ") && hasProject) {
					IsMandatory = false;
					SeqNo = 50;
					addAcctSchemaElement(m_as.getC_AcctSchema_ID(), name, C_Element_ID, IsMandatory, IsBalanced, SeqNo,
							ElementType, getTrxName());
				} else if (ElementType.equals("MC") && hasMCampaign) {
					IsMandatory = false;
					SeqNo = 60;
					addAcctSchemaElement(m_as.getC_AcctSchema_ID(), name, C_Element_ID, IsMandatory, IsBalanced, SeqNo,
							ElementType, getTrxName());
				} else if (ElementType.equals("SR") && hasSRegion) {
					IsMandatory = false;
					SeqNo = 70;
					addAcctSchemaElement(m_as.getC_AcctSchema_ID(), name, C_Element_ID, IsMandatory, IsBalanced, SeqNo,
							ElementType, getTrxName());
				} else if (ElementType.equals("AY") && hasActivity) {
					IsMandatory = false;
					SeqNo = 80;
					addAcctSchemaElement(m_as.getC_AcctSchema_ID(), name, C_Element_ID, IsMandatory, IsBalanced, SeqNo,
							ElementType, getTrxName());
				}
			}
		} finally {
			DB.close(rs, stmt);
			rs = null;
			stmt = null;
		}
		// Create AcctSchema
	}

	protected void addAcctSchemaElement(int p_C_AcctSchema_ID, String name, int p_C_Element_ID, boolean IsMandatory,
			boolean IsBalanced, int SeqNo, String p_ElementType, String trxName) throws Exception {
		X_C_AcctSchema_Element element = new X_C_AcctSchema_Element(m_ctx, 0, trxName);
		element.setElementType(p_ElementType);
		element.setName(name);
		element.setC_AcctSchema_ID(p_C_AcctSchema_ID);
		element.setIsMandatory(IsMandatory);
		element.setIsBalanced(IsBalanced);
		element.setSeqNo(SeqNo);
		element.saveEx();

		String sql = "";
		if (p_ElementType.equals("OO")) {
			sql = "UPDATE C_AcctSchema_Element SET Org_ID= ? WHERE C_AcctSchema_Element_ID= ?";
			DB.executeUpdateEx(sql, new Object[] { getAD_Org_ID(), element.getC_AcctSchema_Element_ID() },
					getTrxName());
		}
		if (p_ElementType.equals("AC")) {
			/** Default value for mandatory elements: OO and AC */
			int C_ElementValue_ID = m_nap.getC_ElementValue_ID("DEFAULT_ACCT");
			if (log.isLoggable(Level.FINE))
				log.fine("C_ElementValue_ID=" + C_ElementValue_ID);
			sql = "UPDATE C_AcctSchema_Element SET C_ElementValue_ID= ?, C_Element_ID=? WHERE C_AcctSchema_Element_ID= ?";
			DB.executeUpdateEx(sql,
					new Object[] { C_ElementValue_ID, p_C_Element_ID, element.getC_AcctSchema_Element_ID() },
					getTrxName());
		}

	}

	// createAccounting
	protected void createAccountingRecord(String tableName) throws Exception {
		MTable table = MTable.get(m_ctx, tableName);
		PO acct = table.getPO(0, getTrxName());

		MColumn[] cols = table.getColumns(false);
		for (MColumn c : cols) {
			if (!c.isActive())
				continue;
			String columnName = c.getColumnName();
			if (c.isStandardColumn()) {
			} else if (DisplayType.Account == c.getAD_Reference_ID()) {
				acct.set_Value(columnName, getAcct(columnName));
				if (log.isLoggable(Level.INFO))
					log.info("Account: " + columnName);
			} else if (DisplayType.YesNo == c.getAD_Reference_ID()) {
				acct.set_Value(columnName, Boolean.TRUE);
				if (log.isLoggable(Level.INFO))
					log.info("YesNo: " + c.getColumnName());
			}
		}
		acct.setAD_Client_ID(m_client.getAD_Client_ID());
		acct.set_Value(I_C_AcctSchema.COLUMNNAME_C_AcctSchema_ID, m_as.getC_AcctSchema_ID());
		//
		if (!acct.save()) {
			throw new AdempiereUserError(CLogger.retrieveErrorString(table.getName() + " not created"));
		}
	}

	/**
	 * Get Account ID for key
	 * 
	 * @param key key
	 * @return C_ValidCombination_ID
	 * @throws AdempiereUserError
	 */
	protected Integer getAcct(String key) throws AdempiereUserError {
		log.fine(key);
		// Element
		int C_ElementValue_ID = m_nap.getC_ElementValue_ID(key.toUpperCase());
		if (C_ElementValue_ID == 0) {
			throw new AdempiereUserError("Account not defined: " + key);
		}

		MAccount vc = MAccount.getDefault(m_as, true); // optional null
		vc.setAD_Org_ID(0); // will be overwritten
		vc.setAccount_ID(C_ElementValue_ID);
		if (!vc.save()) {
			throw new AdempiereUserError("Not Saved - Key=" + key + ", C_ElementValue_ID=" + C_ElementValue_ID);
		}
		int C_ValidCombination_ID = vc.getC_ValidCombination_ID();
		if (C_ValidCombination_ID == 0) {
			throw new AdempiereUserError("No account - Key=" + key + ", C_ElementValue_ID=" + C_ElementValue_ID);
		}
		return C_ValidCombination_ID;
	} // getAcct

	/**
	 * Create GL Category
	 * 
	 * @param Name         name
	 * @param CategoryType category type MGLCategory.CATEGORYTYPE_*
	 * @param isDefault    is default value
	 * @return GL_Category_ID
	 */
	protected int createGLCategory(String Name, String CategoryType, boolean isDefault) {
		MGLCategory cat = new MGLCategory(m_ctx, 0, getTrxName());
		cat.setAD_Org_ID(0);
		cat.setName(Name);
		cat.setCategoryType(CategoryType);
		cat.setIsDefault(isDefault);
		if (!cat.save()) {
			log.log(Level.SEVERE, "GL Category NOT created - " + Name);
			return 0;
		}
		//
		return cat.getGL_Category_ID();
	} // createGLCategory

	/**
	 * Create Document Types with Sequence
	 * 
	 * @param Name                 name
	 * @param PrintName            print name
	 * @param DocBaseType          document base type
	 * @param DocSubTypeSO         sales order sub type
	 * @param C_DocTypeShipment_ID shipment doc
	 * @param C_DocTypeInvoice_ID  invoice doc
	 * @param StartNo              start doc no
	 * @param GL_Category_ID       gl category
	 * @param isReturnTrx          is return trx
	 * @return C_DocType_ID doc type or 0 for error
	 */
	protected int createDocType(String Name, String PrintName, String DocBaseType, String DocSubTypeSO,
			int C_DocTypeShipment_ID, int C_DocTypeInvoice_ID, int StartNo, int GL_Category_ID, boolean isReturnTrx) {
		MSequence sequence = null;
		if (StartNo != 0) {
			sequence = new MSequence(m_ctx, getAD_Client_ID(), Name, StartNo, getTrxName());
			if (!sequence.save()) {
				log.log(Level.SEVERE, "Sequence NOT created - " + Name);
				return 0;
			}
		}

		MDocType dt = new MDocType(m_ctx, DocBaseType, Name, getTrxName());
		if (PrintName != null && PrintName.length() > 0)
			dt.setPrintName(PrintName); // Defaults to Name
		if (DocSubTypeSO != null) {
			if (MDocType.DOCBASETYPE_MaterialPhysicalInventory.equals(DocBaseType)) {
				dt.setDocSubTypeInv(DocSubTypeSO);
			} else {
				dt.setDocSubTypeSO(DocSubTypeSO);
			}
		}
		if (C_DocTypeShipment_ID != 0)
			dt.setC_DocTypeShipment_ID(C_DocTypeShipment_ID);
		if (C_DocTypeInvoice_ID != 0)
			dt.setC_DocTypeInvoice_ID(C_DocTypeInvoice_ID);
		if (GL_Category_ID != 0)
			dt.setGL_Category_ID(GL_Category_ID);
		if (sequence == null)
			dt.setIsDocNoControlled(false);
		else {
			dt.setIsDocNoControlled(true);
			dt.setDocNoSequence_ID(sequence.getAD_Sequence_ID());
		}
		dt.setIsSOTrx();
		if (isReturnTrx)
			dt.setIsSOTrx(!dt.isSOTrx());
		if (!dt.save()) {
			log.log(Level.SEVERE, "DocType NOT created - " + Name);
			return 0;
		}
		//
		return dt.getC_DocType_ID();
	} // createDocType

	/**************************************************************************
	 * Create Default main entities. - Dimensions and BPGroup, Prod Category) -
	 * Location, Locator, Warehouse - PriceList - Cashbook, PaymentTerm
	 * 
	 * @param C_Country_ID  country
	 * @param City          city
	 * @param C_Region_ID   region
	 * @param C_Currency_ID currency
	 * @return true if created
	 */
	public boolean createEntities(int C_Country_ID, String City, int C_Region_ID, int C_Currency_ID, String postal,
			String address1) throws Exception {
		if (m_as == null) {
			log.severe("No AcctountingSChema");
			return false;
		}
		if (log.isLoggable(Level.INFO))
			log.info("C_Country_ID=" + C_Country_ID + ", City=" + City + ", C_Region_ID=" + C_Region_ID);
		m_info.append("\n----\n");
		//
		String defaultName = Msg.translate(m_lang, "Standard");
		StringBuilder sqlCmd = null;
		int no = 0;

		// Create Marketing Channel/Campaign
		X_C_Channel channel = addChannel(defaultName, m_hasMCampaign, getTrxName());
		
		addCampaign(channel, defaultName, m_hasMCampaign, getTrxName());

		// Create Sales Region
		addSalesRegion(defaultName, m_hasSRegion, getTrxName());

		// Create Activity
		addActivity(defaultName, m_hasActivity, getTrxName());

		// Create BP Group
		MBPGroup defaultPartnerGroup = addBPGroup(defaultName, 0, true, getTrxName());

		// Create BPartner
		MBPartner defaultPartner = addBPartner(defaultPartnerGroup, defaultName, 0, true, getTrxName());

		createPreference("C_BPartner_ID", String.valueOf(defaultPartner.getC_BPartner_ID()), 143, getTrxName());

		// Location for Standard BP
		MLocation partnerLoc = addLocation(0, C_Country_ID, C_Region_ID, City, false, getTrxName());
		addPartnerLocation(defaultPartner, partnerLoc, 0, getTrxName());

		// Create Product Category
		MProductCategory productCategory = addProductCategory(0, defaultName, true, getTrxName());
		// TaxCategory
		String taxName = (C_Country_ID == COUNTRY_US) ? "Sales Tax" : defaultName;
		MTaxCategory taxCategory = addTaxCategory(taxName, 0, true, getTrxName());

		// Tax - Zero Rate
		addTax(taxCategory, defaultName, 0, true, getTrxName());

		// Create Product
		MProduct defaultProduct = addProduct(productCategory, taxCategory, 100, defaultName, 0, true, getTrxName());

		// Location (Company)
		MLocation companyLocation = addLocation(0, C_Country_ID, C_Region_ID, City, true, getTrxName());
		companyLocation.setAddress1(address1);
		companyLocation.setPostal(postal);
		companyLocation.saveEx();
		createPreference("C_Country_ID", String.valueOf(C_Country_ID), 0, getTrxName());

		// Default Warehouse Location
		MLocation warehouseLocation = addLocation(0, C_Country_ID, C_Region_ID, City, false, getTrxName());
		warehouseLocation.setAddress1(address1);
		warehouseLocation.setPostal(postal);
		warehouseLocation.saveEx();
		// Default Warehouse
		MWarehouse defaultWarehouse = addWarehouse(warehouseLocation, getAD_Org_ID(), defaultName, getTrxName());
		// Default Locator
		addWarehouseLocator(defaultWarehouse, defaultName, true, getTrxName());

		// Update ClientInfo
		String sql = "UPDATE AD_ClientInfo SET C_BPartnerCashTrx_ID=?,M_ProductFreight_ID= ? WHERE AD_Client_ID= ? ";
		updateRecordInfo(sql,new Object[] { defaultPartner.getC_BPartner_ID(), defaultProduct.getM_Product_ID(), getAD_Client_ID() },
				getTrxName());
		// PriceList
		MPriceList defaultPriceList = addPriceList(0, defaultName, C_Currency_ID, true, getTrxName());
		// Discount Schema
		MDiscountSchema defaultDiscountSchema = addDiscountSchema(0, defaultName,
				MDiscountSchema.DISCOUNTTYPE_Pricelist, getTrxName());
		// PriceList Version
		MPriceListVersion defaultPLV = addPriceListVersion(defaultPriceList, defaultDiscountSchema, 0);
		// ProductPrice
		addProductPrice(defaultPLV, defaultProduct.getM_Product_ID(), Env.ONE, Env.ONE, Env.ONE, getTrxName());

		// Create Sales Rep for Client-User
		MBPartner bpCU = addBPartner(defaultPartnerGroup, clientUser.getName(), 0, false, getTrxName());
		bpCU.setIsEmployee(true);
		bpCU.setIsSalesRep(true);
		if (bpCU.save())
			m_info.append(Msg.translate(m_lang, "SalesRep_ID")).append("=").append(clientUser.getName()).append("\n");
		else
			log.log(Level.SEVERE, "SalesRep (User) NOT inserted");

		// Location for Client-User
		MLocation bpLocCU = addLocation(0, C_Country_ID, C_Region_ID, City, false, getTrxName());
		if (bpLocCU != null)
			addPartnerLocation(bpCU, bpLocCU, 0, getTrxName());

		// Update User
		sqlCmd = new StringBuilder("UPDATE AD_User SET C_BPartner_ID=");
		sqlCmd.append(bpCU.getC_BPartner_ID()).append(" WHERE AD_User_ID=").append(clientUser.getAD_User_ID());
		no = DB.executeUpdateEx(sqlCmd.toString(), getTrxName());
		if (no != 1)
			log.log(Level.SEVERE, "User of SalesRep (User) NOT updated");

		// Create Sales Rep for Client-Admin
		MBPartner bpCA = addBPartner(defaultPartnerGroup, adminClientUser.getName(), 0, false, getTrxName());
		bpCA.setIsEmployee(true);
		bpCA.setIsSalesRep(true);
		if (bpCA.save())
			m_info.append(Msg.translate(m_lang, "SalesRep_ID")).append("=").append(adminClientUser.getName())
					.append("\n");
		else
			log.log(Level.SEVERE, "SalesRep (Admin) NOT inserted");
		// Location for Client-Admin
		MLocation bpLocCA = addLocation(0, C_Country_ID, C_Region_ID, City, false, getTrxName());
		if (bpLocCA != null)
			addPartnerLocation(bpCA, bpLocCA, 0, getTrxName());

		// Update User
		sqlCmd = new StringBuilder("UPDATE AD_User SET C_BPartner_ID=");
		sqlCmd.append(bpCA.getC_BPartner_ID()).append(" WHERE AD_User_ID=").append(adminClientUser.getAD_User_ID());
		no = DB.executeUpdateEx(sqlCmd.toString(), getTrxName());
		if (no != 1)
			log.log(Level.SEVERE, "User of SalesRep (Admin) NOT updated");

		// Payment Term
		String paymentTermName = "Immediate";
		addPaymentTerm(paymentTermName, 0, 0, 0, Env.ZERO, 0, Env.ZERO, true, getTrxName());
		// Project Cycle
		addCycle(defaultName, C_Currency_ID, getTrxName());

		// Create Default Project
		addProject(defaultName, C_Currency_ID, m_hasProject, false, getTrxName());

		// CashBook
		MCashBook cb = new MCashBook(m_ctx, 0, getTrxName());
		cb.setName(defaultName);
		cb.setC_Currency_ID(C_Currency_ID);
		if (cb.save())
			m_info.append(Msg.translate(m_lang, "C_CashBook_ID")).append("=").append(defaultName).append("\n");
		else
			log.log(Level.SEVERE, "CashBook NOT inserted");
		//
		// do not commit if it is a dry run
		return true;
	} // createEntities

	/**
	 * Create Preference
	 * 
	 * @param Attribute    attribute
	 * @param Value        value
	 * @param AD_Window_ID window
	 */
	protected void createPreference(String Attribute, String Value, int AD_Window_ID, String trxName)  throws Exception  {
		MPreference preference = new MPreference(m_ctx, 0, trxName);
		preference.setAttribute(Attribute);
		preference.setValue(Value);
		if (AD_Window_ID > 0)
			preference.setAD_Window_ID(AD_Window_ID);
		preference.saveEx();
	} // createPreference

	/**
	 * Get Client
	 * 
	 * @return AD_Client_ID
	 */
	public int getAD_Client_ID() {
		return m_client.getAD_Client_ID();
	}

	/**
	 * Get AD_Org_ID
	 * 
	 * @return AD_Org_ID
	 */
	public int getAD_Org_ID() {
		return m_org.getAD_Org_ID();
	}

	/**
	 * Get AD_User_ID
	 * 
	 * @return AD_User_ID
	 */
	public int getAD_User_ID() {
		return adminClientUser.getAD_User_ID();
	}

	/**
	 * Get Info
	 * 
	 * @return Info
	 */
	public String getInfo() {
		return m_info.toString();
	}

	/**
	 * 
	 * @return trxName
	 */
	public String getTrxName() {
		return m_TrxName;
	}
	
	/**
	 * Create defaults documents
	 * @throws Exception
	 */
	protected void createDefaultsDocuments() throws Exception {
		// GL Categories
		createGLCategory("Standard", MGLCategory.CATEGORYTYPE_Manual, true);
		int GL_None = createGLCategory("None", MGLCategory.CATEGORYTYPE_Document, false);
		int GL_GL = createGLCategory("Manual", MGLCategory.CATEGORYTYPE_Manual, false);
		int GL_ARI = createGLCategory("AR Invoice", MGLCategory.CATEGORYTYPE_Document, false);
		int GL_ARR = createGLCategory("AR Receipt", MGLCategory.CATEGORYTYPE_Document, false);
		int GL_MM = createGLCategory("Material Management", MGLCategory.CATEGORYTYPE_Document, false);
		int GL_API = createGLCategory("AP Invoice", MGLCategory.CATEGORYTYPE_Document, false);
		int GL_APP = createGLCategory("AP Payment", MGLCategory.CATEGORYTYPE_Document, false);
		int GL_CASH = createGLCategory("Cash/Payments", MGLCategory.CATEGORYTYPE_Document, false);
		int GL_Manufacturing = createGLCategory("Manufacturing", MGLCategory.CATEGORYTYPE_Document, false);
		int GL_Distribution = createGLCategory("Distribution", MGLCategory.CATEGORYTYPE_Document, false);
		int GL_Payroll = createGLCategory("Payroll", MGLCategory.CATEGORYTYPE_Document, false);

		// Base DocumentTypes
		int ii = createDocType("GL Journal", Msg.getElement(m_ctx, "GL_Journal_ID"), MDocType.DOCBASETYPE_GLJournal,
				null, 0, 0, 1000, GL_GL, false);
		if (ii == 0) {
			String err = "Document Type not created";
			m_info.append(err);
			throw new AdempiereException(err);
		}
		createDocType("GL Journal Batch", Msg.getElement(m_ctx, "GL_JournalBatch_ID"), MDocType.DOCBASETYPE_GLJournal,
				null, 0, 0, 100, GL_GL, false);
		// MDocType.DOCBASETYPE_GLDocument
		//
		int DT_I = createDocType("AR Invoice", Msg.getElement(m_ctx, "C_Invoice_ID", true),
				MDocType.DOCBASETYPE_ARInvoice, null, 0, 0, 100000, GL_ARI, false);
		int DT_II = createDocType("AR Invoice Indirect", Msg.getElement(m_ctx, "C_Invoice_ID", true),
				MDocType.DOCBASETYPE_ARInvoice, null, 0, 0, 150000, GL_ARI, false);
		int DT_IC = createDocType("AR Credit Memo", Msg.getMsg(m_ctx, "CreditMemo"), MDocType.DOCBASETYPE_ARCreditMemo,
				null, 0, 0, 170000, GL_ARI, false);
		// MDocType.DOCBASETYPE_ARProFormaInvoice

		createDocType("AP Invoice", Msg.getElement(m_ctx, "C_Invoice_ID", false), MDocType.DOCBASETYPE_APInvoice, null,
				0, 0, 0, GL_API, false);
		int DT_IPC = createDocType("AP CreditMemo", Msg.getMsg(m_ctx, "CreditMemo"), MDocType.DOCBASETYPE_APCreditMemo,
				null, 0, 0, 0, GL_API, false);
		createDocType("Match Invoice", Msg.getElement(m_ctx, "M_MatchInv_ID", false), MDocType.DOCBASETYPE_MatchInvoice,
				null, 0, 0, 390000, GL_API, false);

		createDocType("AR Receipt", Msg.getElement(m_ctx, "C_Payment_ID", true), MDocType.DOCBASETYPE_ARReceipt, null,
				0, 0, 0, GL_ARR, false);
		createDocType("AP Payment", Msg.getElement(m_ctx, "C_Payment_ID", false), MDocType.DOCBASETYPE_APPayment, null,
				0, 0, 0, GL_APP, false);
		createDocType("Allocation", "Allocation", MDocType.DOCBASETYPE_PaymentAllocation, null, 0, 0, 490000, GL_CASH,
				false);

		int DT_S = createDocType("MM Shipment", "Delivery Note", MDocType.DOCBASETYPE_MaterialDelivery, null, 0, 0,
				500000, GL_MM, false);
		int DT_SI = createDocType("MM Shipment Indirect", "Delivery Note", MDocType.DOCBASETYPE_MaterialDelivery, null,
				0, 0, 550000, GL_MM, false);
		int DT_VRM = createDocType("MM Vendor Return", "Vendor Return", MDocType.DOCBASETYPE_MaterialDelivery, null, 0,
				0, 590000, GL_MM, true);

		createDocType("MM Receipt", "Vendor Delivery", MDocType.DOCBASETYPE_MaterialReceipt, null, 0, 0, 0, GL_MM,
				false);
		int DT_RM = createDocType("MM Customer Return", "Customer Return", MDocType.DOCBASETYPE_MaterialReceipt, null,
				0, 0, 570000, GL_MM, true);

		createDocType("Purchase Order", Msg.getElement(m_ctx, "C_Order_ID", false), MDocType.DOCBASETYPE_PurchaseOrder,
				null, 0, 0, 800000, GL_None, false);
		createDocType("Match PO", Msg.getElement(m_ctx, "M_MatchPO_ID", false), MDocType.DOCBASETYPE_MatchPO, null, 0,
				0, 890000, GL_None, false);
		createDocType("Purchase Requisition", Msg.getElement(m_ctx, "M_Requisition_ID", false),
				MDocType.DOCBASETYPE_PurchaseRequisition, null, 0, 0, 900000, GL_None, false);
		createDocType("Vendor Return Material", "Vendor Return Material Authorization",
				MDocType.DOCBASETYPE_PurchaseOrder, MDocType.DOCSUBTYPESO_ReturnMaterial, DT_VRM, DT_IPC, 990000, GL_MM,
				false);

		createDocType("Bank Statement", Msg.getElement(m_ctx, "C_BankStatemet_ID", true),
				MDocType.DOCBASETYPE_BankStatement, null, 0, 0, 700000, GL_CASH, false);
		createDocType("Cash Journal", Msg.getElement(m_ctx, "C_Cash_ID", true), MDocType.DOCBASETYPE_CashJournal, null,
				0, 0, 750000, GL_CASH, false);

		createDocType("Material Movement", Msg.getElement(m_ctx, "M_Movement_ID", false),
				MDocType.DOCBASETYPE_MaterialMovement, null, 0, 0, 610000, GL_MM, false);
		createDocType("Physical Inventory", Msg.getElement(m_ctx, "M_Inventory_ID", false),
				MDocType.DOCBASETYPE_MaterialPhysicalInventory, MDocType.DOCSUBTYPEINV_PhysicalInventory, 0, 0, 620000,
				GL_MM, false);
		createDocType("Material Production", Msg.getElement(m_ctx, "M_Production_ID", false),
				MDocType.DOCBASETYPE_MaterialProduction, null, 0, 0, 630000, GL_MM, false);
		createDocType("Project Issue", Msg.getElement(m_ctx, "C_ProjectIssue_ID", false),
				MDocType.DOCBASETYPE_ProjectIssue, null, 0, 0, 640000, GL_MM, false);
		createDocType("Internal Use Inventory", "Internal Use Inventory",
				MDocType.DOCBASETYPE_MaterialPhysicalInventory, MDocType.DOCSUBTYPEINV_InternalUseInventory, 0, 0,
				650000, GL_MM, false);
		createDocType("Cost Adjustment", "Cost Adjustment", MDocType.DOCBASETYPE_MaterialPhysicalInventory,
				MDocType.DOCSUBTYPEINV_CostAdjustment, 0, 0, 660000, GL_MM, false);

		// Order Entry
		createDocType("Binding offer", "Quotation", MDocType.DOCBASETYPE_SalesOrder, MDocType.DOCSUBTYPESO_Quotation, 0,
				0, 10000, GL_None, false);
		createDocType("Non binding offer", "Proposal", MDocType.DOCBASETYPE_SalesOrder, MDocType.DOCSUBTYPESO_Proposal,
				0, 0, 20000, GL_None, false);
		createDocType("Prepay Order", "Prepay Order", MDocType.DOCBASETYPE_SalesOrder,
				MDocType.DOCSUBTYPESO_PrepayOrder, DT_S, DT_I, 30000, GL_None, false);
		createDocType("Customer Return Material", "Customer Return Material Authorization",
				MDocType.DOCBASETYPE_SalesOrder, MDocType.DOCSUBTYPESO_ReturnMaterial, DT_RM, DT_IC, 30000, GL_None,
				false);
		createDocType("Standard Order", "Order Confirmation", MDocType.DOCBASETYPE_SalesOrder,
				MDocType.DOCSUBTYPESO_StandardOrder, DT_S, DT_I, 50000, GL_None, false);
		createDocType("Credit Order", "Order Confirmation", MDocType.DOCBASETYPE_SalesOrder,
				MDocType.DOCSUBTYPESO_OnCreditOrder, DT_SI, DT_I, 60000, GL_None, false); // RE
		createDocType("Warehouse Order", "Order Confirmation", MDocType.DOCBASETYPE_SalesOrder,
				MDocType.DOCSUBTYPESO_WarehouseOrder, DT_S, DT_I, 70000, GL_None, false); // LS

		// Manufacturing Document
		createDocType("Manufacturing Order", "Manufacturing Order", MDocType.DOCBASETYPE_ManufacturingOrder, null, 0, 0,
				80000, GL_Manufacturing, false);
		createDocType("Manufacturing Cost Collector", "Cost Collector", MDocType.DOCBASETYPE_ManufacturingCostCollector,
				null, 0, 0, 81000, GL_Manufacturing, false);
		createDocType("Maintenance Order", "Maintenance Order", MDocType.DOCBASETYPE_MaintenanceOrder, null, 0, 0,
				86000, GL_Manufacturing, false);
		createDocType("Quality Order", "Quality Order", MDocType.DOCBASETYPE_QualityOrder, null, 0, 0, 87000,
				GL_Manufacturing, false);
		createDocType("Distribution Order", "Distribution Order", MDocType.DOCBASETYPE_DistributionOrder, null, 0, 0,
				88000, GL_Distribution, false);
		// Payroll
		createDocType("Payroll", "Payroll", MDocType.DOCBASETYPE_Payroll, null, 0, 0, 90000, GL_Payroll, false);

		int DT = createDocType("POS Order", "Order Confirmation", MDocType.DOCBASETYPE_SalesOrder,
				MDocType.DOCSUBTYPESO_POSOrder, DT_SI, DT_II, 80000, GL_None, false); // Bar
		// POS As Default for window SO
		createPreference("C_DocTypeTarget_ID", String.valueOf(DT), 143, getTrxName());

		// Validate Completeness
		validateDocumentTypes();
	}
	
	/**
	 * Add project
	 * @param defaultName
	 * @param p_Currency_ID
	 * @param hasProject
	 * @param isSummary
	 * @param trxName
	 */
	protected void addProject(String defaultName, int p_Currency_ID, boolean hasProject, boolean isSummary,
			String trxName) {
		MProject project = new MProject(m_ctx, 0, trxName);
		project.setC_Currency_ID(p_Currency_ID);
		project.setValue(defaultName);
		project.setName(defaultName);
		if (project.save()) {
			if (hasProject) {
				String sql = "UPDATE C_AcctSchema_Element SET C_Project_ID= ? WHERE C_AcctSchema_ID= ? AND ElementType ='PJ'";
				DB.executeUpdateEx(sql, new Object[] { project.getC_Project_ID(), m_as.getC_AcctSchema_ID() }, trxName);
			}
		}
	}

	/**
	 * Add new cycle
	 * @param defaultName
	 * @param p_Currency_ID
	 * @param trxName
	 */
	protected void addCycle(String defaultName, int p_Currency_ID, String trxName) {
		X_C_Cycle cycle = new X_C_Cycle(m_ctx, 0, trxName);
		cycle.setC_Currency_ID(p_Currency_ID);
		cycle.setName(defaultName);
		cycle.saveEx();
	}

	/**
	 * Add payment term
	 * @param paymentTermName
	 * @param p_NetDays
	 * @param p_GraceDays
	 * @param p_DiscountDays
	 * @param p_Discount
	 * @param p_DiscountDays2
	 * @param p_Discount2
	 * @param p_IsDefault
	 * @param trxName
	 */
	protected MPaymentTerm addPaymentTerm(String paymentTermName, int p_NetDays, int p_GraceDays, int p_DiscountDays,
			BigDecimal p_Discount, int p_DiscountDays2, BigDecimal p_Discount2, boolean p_IsDefault, String trxName) {

		MPaymentTerm paymentTerm = new MPaymentTerm(m_ctx, 0, trxName);
		paymentTerm.setName(paymentTermName);
		paymentTerm.setNetDays(p_NetDays);
		paymentTerm.setGraceDays(p_GraceDays);
		paymentTerm.setDiscountDays(p_DiscountDays);
		paymentTerm.setDiscount(p_Discount);
		paymentTerm.setDiscountDays2(p_DiscountDays2);
		paymentTerm.setDiscount2(p_Discount2);
		paymentTerm.setIsDefault(p_IsDefault);
		paymentTerm.saveEx();
		return paymentTerm;
	}
	
	/**
	 * Add payment term
	 * @param paymentTermName
	 * @param p_NetDays
	 * @param p_GraceDays
	 * @param p_DiscountDays
	 * @param p_Discount
	 * @param p_DiscountDays2
	 * @param p_Discount2
	 * @param p_IsDefault
	 * @param trxName
	 */
	protected MPaySchedule addPaySchedule(MPaymentTerm paymentTerm, BigDecimal p_Percentage, int p_NetDays, int p_DiscountDays, BigDecimal p_Discount, String trxName) {

		MPaySchedule paySchedule = new MPaySchedule(m_ctx, 0, trxName);
		paySchedule.setParent(paymentTerm);
		paySchedule.setC_PaymentTerm_ID(paymentTerm.getC_PaymentTerm_ID());
		paySchedule.setPercentage(p_Percentage);
		paySchedule.setNetDays(p_NetDays);
		paySchedule.setDiscountDays(p_DiscountDays);
		paySchedule.setDiscount(p_Discount);
		paySchedule.saveEx();
		return paySchedule;
	}

	/**
	 * Add product prices
	 * @param defaultPLV
	 * @param p_Product_ID
	 * @param p_PriceList
	 * @param p_PriceStd
	 * @param p_PriceLimit
	 * @param trxName
	 * @return
	 */
	protected MProductPrice addProductPrice(MPriceListVersion defaultPLV, int p_Product_ID, BigDecimal p_PriceList,
			BigDecimal p_PriceStd, BigDecimal p_PriceLimit, String trxName) {
		MProductPrice pp = new MProductPrice(m_ctx, defaultPLV.getM_PriceList_Version_ID(), p_Product_ID, trxName);
		if (p_PriceList != null)
			pp.setPriceList(p_PriceList);
		if (p_PriceStd != null)
			pp.setPriceStd(p_PriceStd);
		if (p_PriceLimit != null)
			pp.setPriceLimit(p_PriceLimit);
		pp.saveEx();
		m_info.append(Msg.translate(m_lang, "M_ProductPrice_ID")).append(" inserted").append("\n");
		return pp;
	}

	/**
	 * Add prices list version
	 * @param defaultPriceList
	 * @param p_DiscountSchema
	 * @param p_AD_Org_ID
	 * @return
	 */
	protected MPriceListVersion addPriceListVersion(MPriceList defaultPriceList, MDiscountSchema p_DiscountSchema,
			int p_AD_Org_ID) {
		MPriceListVersion plv = new MPriceListVersion(defaultPriceList);
		int orgID = p_AD_Org_ID > 0 ? p_AD_Org_ID : 0;
		plv.setAD_Org_ID(orgID);
		plv.setName();
		plv.setM_DiscountSchema_ID(p_DiscountSchema.getM_DiscountSchema_ID());
		plv.saveEx();
		m_info.append(Msg.translate(m_lang, "M_PriceListVersion_ID")).append("=").append(plv.getName())
				.append("\n");
		return plv;
	}

	/**
	 * Add discount schema
	 * @param p_AD_Org_ID
	 * @param defaultName
	 * @param p_DiscountType
	 * @param trxName
	 * @return
	 */
	protected MDiscountSchema addDiscountSchema(int p_AD_Org_ID, String defaultName, String p_DiscountType,
			String trxName) {
		MDiscountSchema ds = new MDiscountSchema(m_ctx, 0, trxName);
		int orgID = p_AD_Org_ID > 0 ? p_AD_Org_ID : 0;
		ds.setAD_Org_ID(orgID);
		ds.setName(defaultName);
		ds.setDiscountType(p_DiscountType);
		ds.saveEx();
		addDiscountSchemaLine(ds, MConversionType.getDefault(getAD_Client_ID()), Env.getContextAsDate(m_ctx, Env.DATE), trxName);
		m_info.append(Msg.translate(m_lang, "M_DiscountSchema_ID")).append("=").append(defaultName).append("\n");
		return ds;
	}
	
	/**
	 * Add Discount schema Line
	 * @param defaultDiscountSchema
	 * @param p_C_ConversionType_ID
	 * @param convertDate 
	 * @param p_TrxName
	 * @return
	 */
	protected MDiscountSchemaLine addDiscountSchemaLine(MDiscountSchema defaultDiscountSchema, int p_C_ConversionType_ID, Timestamp convertDate, String p_TrxName) {
		MDiscountSchemaLine dsl = new MDiscountSchemaLine(m_ctx, 0, p_TrxName);
		dsl.setC_ConversionType_ID(p_C_ConversionType_ID);
		dsl.setM_DiscountSchema_ID(defaultDiscountSchema.getM_DiscountSchema_ID());
		dsl.setConversionDate(convertDate);
		dsl.setSeqNo(10);
		dsl.setList_Base(X_M_DiscountSchemaLine.LIST_BASE_ListPrice);
		dsl.setList_AddAmt(Env.ZERO)  ;
		dsl.setList_Discount(Env.ZERO)  ;
		dsl.setStd_Base(X_M_DiscountSchemaLine.STD_BASE_ListPrice)  ;
		dsl.setStd_AddAmt(Env.ZERO)  ;
		dsl.setStd_Discount(Env.ZERO)  ;
		dsl.setLimit_Base(X_M_DiscountSchemaLine.LIMIT_BASE_ListPrice)  ;
		dsl.setLimit_AddAmt(Env.ZERO)  ;
		dsl.setLimit_Discount(Env.ZERO)  ;
		dsl.setList_MinAmt(Env.ZERO)  ;
		dsl.setList_MaxAmt(Env.ZERO)  ;
		dsl.setList_Rounding(X_M_DiscountSchemaLine.LIST_ROUNDING_CurrencyPrecision)  ;
		dsl.setStd_MinAmt(Env.ZERO)  ;
		dsl.setStd_MaxAmt(Env.ZERO)  ;
		dsl.setStd_Rounding(X_M_DiscountSchemaLine.LIST_ROUNDING_CurrencyPrecision)  ;
		dsl.setLimit_MinAmt(Env.ZERO)  ;
		dsl.setLimit_MaxAmt(Env.ZERO)  ;
		dsl.setLimit_Rounding(X_M_DiscountSchemaLine.LIST_ROUNDING_CurrencyPrecision)  ;
		dsl.saveEx();
		return dsl;
	}
	
	protected void addProductPO(MProduct product, MBPartner vendorPartner, int p_C_Currency_ID, String trxName) {
		MProductPO productPO = new MProductPO(m_ctx, 0, trxName);
		productPO.setM_Product_ID(product.getM_Product_ID());
		productPO.setVendorProductNo(product.getValue());
		productPO.setC_Currency_ID(p_C_Currency_ID);
		productPO.setC_BPartner_ID(vendorPartner.getC_BPartner_ID());
		productPO.saveEx();
	}

	/**
	 * Add price list
	 * @param p_AD_Org_ID
	 * @param defaultName
	 * @param p_Currency_ID
	 * @param isDefault
	 * @param trxName
	 * @return
	 */
	protected MPriceList addPriceList(int p_AD_Org_ID, String defaultName, int p_Currency_ID, boolean isDefault,
			String trxName) {
		MPriceList pl = new MPriceList(m_ctx, 0, trxName);
		int orgID = p_AD_Org_ID > 0 ? p_AD_Org_ID : 0;
		pl.setAD_Org_ID(orgID);
		pl.setName(defaultName);
		pl.setC_Currency_ID(p_Currency_ID);
		pl.setIsDefault(isDefault);
		pl.saveEx();
		m_info.append(Msg.translate(m_lang, "M_PriceList_ID")).append("=").append(defaultName).append("\n");
		return pl;
	}

	/**
	 * Update record info
	 * @param sql
	 * @param parameters
	 * @param trxName
	 * @return
	 * @throws Exception
	 */
	protected boolean updateRecordInfo(String sql, Object[] parameters, String trxName) throws Exception {
		return DB.executeUpdateEx(sql, parameters, trxName) != 1;
	}

	/**
	 * Add warehouse locator
	 * @param p_Warehouse
	 * @param p_Name
	 * @param isDefault
	 * @param trxName
	 * @return
	 */
	protected MLocator addWarehouseLocator(MWarehouse p_Warehouse, String p_Name, boolean isDefault, String trxName) {
		MLocator locator = new MLocator(p_Warehouse, p_Name);
		locator.setIsDefault(isDefault);
		if (locator.save()) {
			m_info.append(Msg.translate(m_lang, "M_Locator_ID")).append("=").append(p_Name).append("\n");
			return locator;
		}
		log.log(Level.SEVERE, "Locator NOT inserted");
		return null;
	}

	/**
	 * Add warehouse
	 * @param warehouseLocation
	 * @param p_AD_Org_ID
	 * @param defaultName
	 * @param trxName
	 * @return
	 */
	protected MWarehouse addWarehouse(MLocation warehouseLocation, int p_AD_Org_ID, String defaultName,
			String trxName) {
		MWarehouse wh = new MWarehouse(m_ctx, 0, trxName);
		int orgID = p_AD_Org_ID > 0 ? p_AD_Org_ID : 0;
		wh.setAD_Org_ID(orgID);
		wh.setValue(defaultName);
		wh.setName(defaultName);
		wh.setC_Location_ID(warehouseLocation.getC_Location_ID());
		if (wh.save()) {
			m_info.append(Msg.translate(m_lang, "M_Warehouse_ID")).append("=").append(defaultName).append("\n");
			return wh;
		}
		log.log(Level.SEVERE, "Warehouse NOT inserted");

		return null;
	}

	/**
	 * Add product
	 * @param productCategory
	 * @param taxCategory
	 * @param p_UOM_ID
	 * @param defaultName
	 * @param p_AD_Org_ID
	 * @param isHasProduct
	 * @param trxName
	 * @return
	 */
	protected MProduct addProduct(MProductCategory productCategory, MTaxCategory taxCategory, int p_UOM_ID,
			String defaultName, int p_AD_Org_ID, boolean isHasProduct, String trxName) {
		MProduct product = new MProduct(m_ctx, 0, trxName);
		int orgID = p_AD_Org_ID > 0 ? p_AD_Org_ID : 0;
		product.setAD_Org_ID(orgID);
		product.setValue(defaultName);
		product.setName(defaultName);
		product.setC_UOM_ID(p_UOM_ID);
		product.setM_Product_Category_ID(productCategory.getM_Product_Category_ID());
		product.setC_TaxCategory_ID(taxCategory.getC_TaxCategory_ID());
		if (product.save()) {
			m_info.append(Msg.translate(m_lang, "M_Product_ID")).append("=").append(defaultName).append("\n");
			if (isHasProduct) {
				String sql = "UPDATE C_AcctSchema_Element SET M_Product_ID= ? WHERE C_AcctSchema_ID= ? AND ElementType ='PR'";
				DB.executeUpdateEx(sql, new Object[] { product.getM_Product_ID(), m_as.getC_AcctSchema_ID() }, trxName);
			}
			return product;
		}
		log.log(Level.SEVERE, "Product NOT inserted");
		return null;
	}
	
	/**
	 * Add tax
	 * @param p_TaxCategory
	 * @param defaultName
	 * @param p_AD_Org_ID
	 * @param isDefault
	 * @param trxName
	 * @return
	 */
	protected MTax addTax(MTaxCategory p_TaxCategory, String defaultName, int p_AD_Org_ID, boolean isDefault,
			String trxName) {
		MTax tax = new MTax(m_ctx, defaultName, Env.ZERO, p_TaxCategory.getC_TaxCategory_ID(), trxName);
		int orgID = p_AD_Org_ID > 0 ? p_AD_Org_ID : 0;
		tax.setAD_Org_ID(orgID);
		tax.setIsDefault(isDefault);
		if (tax.save()) {
			m_info.append(Msg.translate(m_lang, "C_Tax_ID")).append("=").append(tax.getName()).append("\n");
			return tax;
		}
		log.log(Level.SEVERE, "Tax NOT inserted");
		return null;
	}
	
	/**
	 * Add tax category
	 * @param taxName
	 * @param p_AD_Org_ID
	 * @param isDefault
	 * @param trxName
	 * @return
	 */
	protected MTaxCategory addTaxCategory(String taxName, int p_AD_Org_ID, boolean isDefault, String trxName) {

		MTaxCategory taxCategory = new MTaxCategory(m_ctx, 0, trxName);
		taxCategory.setName(taxName);
		taxCategory.setIsDefault(isDefault);
		int orgID = p_AD_Org_ID > 0 ? p_AD_Org_ID : 0;
		taxCategory.setAD_Org_ID(orgID);

		if (taxCategory.save()) {
			m_info.append(Msg.translate(m_lang, "C_TaxCategory_ID")).append("=").append(taxName).append("\n");
			return taxCategory;
		}
		log.log(Level.SEVERE, "TaxCategory NOT inserted");
		return null;
	}

	/**
	 * Add product category
	 * @param p_AD_Org_ID
	 * @param defaultName
	 * @param b
	 * @param trxName
	 * @return
	 */
	protected MProductCategory addProductCategory(int p_AD_Org_ID, String defaultName, boolean isDefault, String trxName) {
		MProductCategory pc = new MProductCategory(m_ctx, 0, trxName);
		int orgID = p_AD_Org_ID > 0 ? p_AD_Org_ID : 0;
		pc.setAD_Org_ID(orgID);
		pc.setValue(defaultName);
		pc.setName(defaultName);
		pc.setIsDefault(isDefault);
		if (pc.save()) {
			m_info.append(Msg.translate(m_lang, "M_Product_Category_ID")).append("=").append(defaultName).append("\n");
			return pc;
		}
		log.log(Level.SEVERE, "Product Category NOT inserted");
		return null;
	}

	/**
	 * Add location
	 * @param p_AD_Org_ID
	 * @param p_Country_ID
	 * @param p_Region_ID
	 * @param p_City
	 * @param isByCompany
	 * @param trxName
	 * @return
	 * @throws Exception
	 */
	protected MLocation addLocation(int p_AD_Org_ID, int p_Country_ID, int p_Region_ID, String p_City,
			boolean isByCompany, String trxName) throws Exception {
		MLocation loc = new MLocation(m_ctx, p_Country_ID, p_Region_ID, p_City, trxName);
		int orgID = p_AD_Org_ID > 0 ? p_AD_Org_ID : 0;
		loc.setAD_Org_ID(orgID);
		loc.saveEx();
		if (isByCompany) {
			String sql = "UPDATE AD_OrgInfo SET C_Location_ID= ? WHERE AD_Org_ID= ? ";
			DB.executeUpdateEx(sql, new Object[] { loc.getC_Location_ID(), getAD_Org_ID() }, trxName);
		}
		return loc;
	}

	/**
	 * Add partner location
	 * @param p_Partner
	 * @param p_Location
	 * @param p_AD_Org_ID
	 * @param trxName
	 * @return
	 * @throws Exception
	 */
	protected MBPartnerLocation addPartnerLocation(MBPartner p_Partner, MLocation p_Location, int p_AD_Org_ID,
			String trxName) throws Exception {
		MBPartnerLocation bpl = new MBPartnerLocation(p_Partner);
		bpl.setC_Location_ID(p_Location.getC_Location_ID());
		bpl.saveEx();
		return bpl;
	}

	/**
	 * Add business partner
	 * @param bpGroup
	 * @param defaultName
	 * @param p_AD_Org_ID
	 * @param isHasPartner
	 * @param trxName
	 * @return
	 * @throws Exception
	 */
	protected MBPartner addBPartner(MBPGroup bpGroup, String defaultName, int p_AD_Org_ID, boolean isHasPartner,
			String trxName) throws Exception {
		MBPartner bp = new MBPartner(m_ctx, 0, trxName);
		int orgID = p_AD_Org_ID > 0 ? p_AD_Org_ID : 0;
		bp.setAD_Org_ID(orgID);
		bp.setValue(defaultName);
		bp.setName(defaultName);
		bp.setBPGroup(bpGroup);
		bp.saveEx();
		m_info.append(Msg.translate(m_lang, "C_BPartner_ID")).append("=").append(defaultName).append("\n");
		if (isHasPartner) {
			String sql = "UPDATE C_AcctSchema_Element SET C_BPartner_ID= ? WHERE C_AcctSchema_ID= ? AND ElementType ='BP'";
			DB.executeUpdateEx(sql, new Object[] { bp.getC_BPartner_ID(), m_as.getC_AcctSchema_ID() }, trxName);
		}
		return bp;
	}

	/**
	 * Add business partner group
	 * @param defaultName
	 * @param p_AD_Org_ID
	 * @param isDefault
	 * @param trxName
	 * @return
	 * @throws Exception
	 */
	protected MBPGroup addBPGroup(String defaultName, int p_AD_Org_ID, boolean isDefault, String trxName) throws Exception {
		MBPGroup bpGroup = new MBPGroup(m_ctx, 0, trxName);
		int orgID = p_AD_Org_ID > 0 ? p_AD_Org_ID : 0;
		bpGroup.setAD_Org_ID(orgID);
		bpGroup.setValue(defaultName);
		bpGroup.setName(defaultName);
		bpGroup.setIsDefault(isDefault);
		bpGroup.saveEx();
		m_info.append(Msg.translate(m_lang, "C_BP_Group_ID")).append("=").append(defaultName).append("\n");
		return bpGroup;
	}

	/**
	 * Add activity
	 * @param defaultName
	 * @param isHasActivity
	 * @param trxName
	 * @return
	 * @throws Exception
	 */
	protected MActivity addActivity(String defaultName, boolean isHasActivity, String trxName) throws Exception {
		MActivity activity = new MActivity(m_ctx, 0, trxName);
		activity.setName(defaultName);
		activity.saveEx();

		if (isHasActivity) {
			String sql = "UPDATE C_AcctSchema_Element SET C_Activity_ID= ? WHERE C_AcctSchema_ID= ? AND ElementType ='SR'";
			DB.executeUpdateEx(sql, new Object[] { activity.getC_Activity_ID(), m_as.getC_AcctSchema_ID() }, trxName);
		}
		return activity;
	}

	/**
	 * Add sales region
	 * @param defaultName
	 * @param isHasRegion
	 * @param trxName
	 * @return
	 * @throws Exception
	 */
	protected MSalesRegion addSalesRegion(String defaultName, boolean isHasRegion, String trxName) throws Exception  {
		MSalesRegion salesRegion = new MSalesRegion(m_ctx, 0, trxName);
		salesRegion.setName(defaultName);
		salesRegion.saveEx();

		if (isHasRegion) {
			String sql = "UPDATE C_AcctSchema_Element SET C_SalesRegion_ID= ? WHERE C_AcctSchema_ID= ? AND ElementType ='SR'";
			DB.executeUpdateEx(sql, new Object[] { salesRegion.getC_SalesRegion_ID(), m_as.getC_AcctSchema_ID() },
					trxName);
		}
		return salesRegion;
	}

	/**
	 * Add channel
	 * @param name
	 * @param isHasCampign
	 * @param trxName
	 * @return
	 * @throws Exception
	 */
	protected X_C_Channel addChannel(String name, boolean isHasCampign, String trxName) throws Exception{
		X_C_Channel channel = new X_C_Channel(m_ctx, 0, trxName);
		channel.setName(name);
		channel.saveEx();
		return channel;
	}
	
	/**
	 * Add campaign
	 * @param channel
	 * @param name
	 * @param isHasCampign
	 * @param trxName
	 * @return
	 * @throws Exception
	 */
	protected X_C_Campaign addCampaign(X_C_Channel channel, String name, boolean isHasCampign, String trxName) throws Exception {
		X_C_Campaign campaing = new X_C_Campaign(m_ctx, 0, trxName);
		campaing.setName(name);
		campaing.setC_Channel_ID(channel.getC_Channel_ID());
		campaing.saveEx();

		if (isHasCampign) {
			String sql = "UPDATE C_AcctSchema_Element SET C_Campaign_ID= ? WHERE C_AcctSchema_ID= ? AND ElementType ='MC'";
			DB.executeUpdateEx(sql, new Object[] { campaing.getC_Campaign_ID(), m_as.getC_AcctSchema_ID() }, trxName);
		}
		return campaing;
	}
	
	/**
	 * Add org info
	 * @param p_AD_Org_ID
	 * @param trxName
	 * @return
	 */
	protected MOrgInfo addOrgInfo(int p_AD_Org_ID, String trxName) {
		return MOrgInfo.getCopy(m_ctx, p_AD_Org_ID, trxName);
	}

	/**
	 * Add organization
	 * @param m_client2
	 * @param orgValue
	 * @param name
	 * @return
	 * @throws Exception
	 */
	protected MOrg addOrganization(MClient m_client2, String orgValue, String name) throws Exception{
		MOrg org = new MOrg(m_client, orgValue, name);
		org.saveEx();
		return org;
	}

	/**
	 * Add processor
	 * @param processor
	 * @param adminClientUser
	 * @throws Exception
	 */
	protected void addProcessors(Class<?> processor, MUser adminClientUser) throws Exception {
		if (MAcctProcessor.class.isInstance(processor)) {
			MAcctProcessor ap = new MAcctProcessor(m_client, adminClientUser.getAD_User_ID());
			ap.setAD_Schedule_ID(SCHEDULE_10_MINUTES);
			ap.saveEx();
		} else if (MRequestProcessor.class.isInstance(processor)) {
			MRequestProcessor rp = new MRequestProcessor(m_client, adminClientUser.getAD_User_ID());
			rp.setAD_Schedule_ID(SCHEDULE_15_MINUTES);
			rp.saveEx();
		}
	}

	/**
	 * Add user role
	 * @param user
	 * @param role
	 * @param trxName
	 * @throws Exception
	 */
	protected void addUserRole(MUser user, MRole role, String trxName) throws Exception {
		// ClientUser - Admin & User
		X_AD_User_Roles userRole = new X_AD_User_Roles(m_ctx, 0, trxName);
		userRole.setAD_Role_ID(role.getAD_Role_ID());
		userRole.setAD_User_ID(user.getAD_User_ID());
		userRole.saveEx();
	}

	/**
	 * Add user
	 * @param p_AD_Client_ID
	 * @param name
	 * @param email
	 * @param isSetInitialPassword
	 * @param trxName
	 * @return
	 * @throws Exception
	 */
	protected MUser addUser(int p_AD_Client_ID, String name, String email, boolean isSetInitialPassword,
			String trxName) throws Exception {
		MUser user = new MUser(m_ctx, 0, trxName);
		if (isSetInitialPassword)
			user.setPassword(name);
		user.setDescription(name);
		user.setName(name);
		user.setAD_Client_ID(p_AD_Client_ID);
		user.setAD_Org_ID(0);
		user.setEMail(email);
		user.saveEx();
		return user;
	}

	/**
	 * Add organization access by role
	 * @param role
	 * @param p_AD_Org_ID
	 * @throws Exception
	 */
	protected void addOrgAccessRole(MRole role, int p_AD_Org_ID) throws Exception {
		// OrgAccess x, 0
		MRoleOrgAccess adminClientAccess = new MRoleOrgAccess(role, p_AD_Org_ID);
		adminClientAccess.saveEx();
	}

	/**
	 * Add role
	 * @param name
	 * @param isAdmin
	 * @param p_UserLevel
	 * @param p_PreferenceType
	 * @param isAccessAvanced
	 * @param trxName
	 * @return
	 * @throws Exception
	 */
	protected MRole addRole(String name, boolean isAdmin, String p_UserLevel, String p_PreferenceType,
			boolean isAccessAvanced, String trxName) throws Exception {
		MRole role = new MRole(m_ctx, 0, trxName);
		role.setClientOrg(m_client);
		role.setName(name);
		role.setUserLevel(p_UserLevel);
		role.setPreferenceType(p_PreferenceType);
		role.setIsShowAcct(true);
		role.setIsAccessAdvanced(isAccessAvanced);
		role.setIsClientAdministrator(isAdmin);
		role.saveEx();
		return role;
	}
	
	/**
	 * Validate document types
	 * @throws Exception
	 */
	protected void validateDocumentTypes() throws Exception{
		ProcessInfo processInfo = new ProcessInfo("Document Type Verify", 0);
		processInfo.setAD_Client_ID(getAD_Client_ID());
		processInfo.setAD_User_ID(getAD_User_ID());
		processInfo.setParameter(new ProcessInfoParameter[0]);
		processInfo.setClassName("org.compiere.process.DocumentTypeVerify");
		if (!ProcessUtil.startJavaProcess(m_ctx, processInfo, m_trx, false)) {
			String err = "Document type verification failed. Message=" + processInfo.getSummary();
			log.log(Level.SEVERE, err);
			m_info.append(err);
			throw new AdempiereException(err);
		}
	}

	protected MBank addBank(int i, String name, String routingNo, String switchCode, boolean isOwnBank, String trxName) {
		MBank bank = new MBank(m_ctx, 0, trxName);
		bank.setName(name);
		bank.setRoutingNo(routingNo);
		bank.setIsOwnBank(isOwnBank);
		bank.setSwiftCode(switchCode);
		bank.saveEx();
		return bank;
	}

	protected MBankAccount addBankAccount(MBank bank, String accountNo, int currency_ID, String accountType, String trxName) {
		MBankAccount bankAccount = new MBankAccount(m_ctx, 0, trxName);
		bankAccount.setC_Bank_ID(bank.getC_Bank_ID());
		bankAccount.setAccountNo(accountNo);
		bankAccount.setC_Currency_ID(currency_ID);
		bankAccount.setBankAccountType(accountType);
		bankAccount.saveEx();
		return bankAccount;
	}

	protected void addBankAccountDoc(MBankAccount bankAccount, String name, String paymentRule, String trxName) {
		X_C_BankAccountDoc bankAccountDoc = new X_C_BankAccountDoc(m_ctx, 0, trxName);
		bankAccountDoc.setC_BankAccount_ID(bankAccount.getC_BankAccount_ID());
		bankAccountDoc.setName(name);
		bankAccountDoc.setPaymentRule(paymentRule);
		bankAccountDoc.saveEx();
	}
} // MSetup
