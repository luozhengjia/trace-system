package com.ejunhai.trace.controller;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ejunhai.trace.common.base.BaseController;
import com.ejunhai.trace.common.base.Pagination;
import com.ejunhai.trace.common.constant.CommonConstant;
import com.ejunhai.trace.common.errors.JunhaiAssert;
import com.ejunhai.trace.merchant.model.MerchantInfo;
import com.ejunhai.trace.merchant.service.MerchantService;
import com.ejunhai.trace.merchant.utils.MerchantUtil;
import com.ejunhai.trace.system.dto.SystemOperateLogDto;
import com.ejunhai.trace.system.dto.SystemPrivilageDto;
import com.ejunhai.trace.system.dto.SystemRoleDto;
import com.ejunhai.trace.system.dto.SystemUserDto;
import com.ejunhai.trace.system.enums.RoleType;
import com.ejunhai.trace.system.enums.UserState;
import com.ejunhai.trace.system.enums.UserType;
import com.ejunhai.trace.system.model.SystemAction;
import com.ejunhai.trace.system.model.SystemOperateLog;
import com.ejunhai.trace.system.model.SystemPrivilage;
import com.ejunhai.trace.system.model.SystemRole;
import com.ejunhai.trace.system.model.SystemUser;
import com.ejunhai.trace.system.service.SystemActionService;
import com.ejunhai.trace.system.service.SystemOperateLogService;
import com.ejunhai.trace.system.service.SystemPrivilageService;
import com.ejunhai.trace.system.service.SystemRoleService;
import com.ejunhai.trace.system.service.SystemUserService;
import com.ejunhai.trace.system.utils.SystemActionUtil;
import com.ejunhai.trace.system.utils.SystemPrivilageUtil;
import com.ejunhai.trace.system.utils.SystemRoleUtil;
import com.ejunhai.trace.system.utils.SystemUserUtil;
import com.ejunhai.trace.utils.SessionManager;

@Controller
@RequestMapping("system")
public class SystemUserController extends BaseController {

    @Resource
    private SystemUserService systemUserService;

    @Resource
    private SystemRoleService systemRoleService;

    @Resource
    private SystemActionService systemActionService;

    @Resource
    private SystemPrivilageService systemPrivilageService;

    @Resource
    private MerchantService merchantService;

    @Resource
    private SystemOperateLogService systemOperateLogService;

    @RequestMapping("/userList")
    public String userList(HttpServletRequest request, SystemUserDto systemUserDto, ModelMap modelMap) {
        systemUserDto.setMerchantId(SessionManager.get(request).getMerchantId());
        Integer iCount = systemUserService.querySystemUserCount(systemUserDto);
        Pagination pagination = new Pagination(systemUserDto.getPageNo(), iCount);

        List<SystemUser> systemUserList = new ArrayList<SystemUser>();
        if (iCount > 0) {
            systemUserDto.setOffset(pagination.getOffset());
            systemUserDto.setPageSize(pagination.getPageSize());
            systemUserList = systemUserService.querySystemUserList(systemUserDto);

            // 获取商户ID-商户映射关系
            List<Integer> merchantIdList = SystemUserUtil.getMerchantIdList(systemUserList);
            List<MerchantInfo> merchantInfoList = merchantService.getMerchantListByIds(merchantIdList);
            Map<String, MerchantInfo> merchantMap = MerchantUtil.getMerchantMap(merchantInfoList);
            modelMap.put("merchantMap", merchantMap);
        }

        modelMap.put("systemUserDto", systemUserDto);
        modelMap.put("systemUserList", systemUserList);
        modelMap.put("pagination", pagination);
        return "system/userList";
    }

    @RequestMapping("/userDetail")
    public String userDetail(HttpServletRequest request, Integer userId, ModelMap modelMap) {
        Integer merchantId = SessionManager.get(request).getMerchantId();

        // 验证用户是否有操作权限
        Integer userType = SessionManager.get(request).getUserType();
        if (userId != null) {
            SystemUser systemUser = systemUserService.read(userId);
            userType = UserType.get(systemUser.getUserType()).getValue();
            modelMap.put("systemUser", systemUser);
            JunhaiAssert.isTrue(merchantId == null || merchantId.equals(systemUser.getMerchantId()), "id无效");
        }

        // 获取可以分配给用户的角色列表
        SystemRoleDto systemRoleDto = new SystemRoleDto();
        if (UserType.ssa.getValue().equals(userType) || UserType.sa.getValue().equals(userType)) {
            systemRoleDto.setRoleType(RoleType.sa.getValue());

            // 获取商户列表
            List<MerchantInfo> merchantInfoList = merchantService.getNoSmaMerchantList();
            modelMap.put("merchantInfoList", merchantInfoList);
            modelMap.put("userType", UserType.sma);
        } else {
            systemRoleDto.setRoleType(RoleType.ma.getValue());
            systemRoleDto.setMerchantId(merchantId);
            modelMap.put("userType", UserType.ma);
        }
        systemRoleDto.setOffset(0);
        systemRoleDto.setPageSize(Integer.MAX_VALUE);
        List<SystemRole> systemRoleList = systemRoleService.querySystemRoleList(systemRoleDto);
        modelMap.put("systemRoleList", systemRoleList);
        modelMap.put("systemRoleMap", SystemRoleUtil.getSystemRoleMap(systemRoleList));

        // 新增或编辑用户信息
        return userId == null ? "system/userAdd" : "system/userEdit";
    }

    @RequestMapping("/addUser")
    @ResponseBody
    public String addUser(HttpServletRequest request, SystemUserDto systemUserDto) {
        JunhaiAssert.notBlank(systemUserDto.getLoginName(), "登陆账号不能为空");
        JunhaiAssert.notBlank(systemUserDto.getPasswd(), "登陆密码不能为空");
        JunhaiAssert.notBlank(systemUserDto.getNickname(), "用户昵称不能为空不能为空");
        JunhaiAssert.notBlank(systemUserDto.getTelephone(), "手机号码不能为空");

        // 暂时限定平台管理员只能创建商户户主，商户户主只能创建商户管理员
        Integer userType = UserType.ma.getValue();
        Integer merchantId = SessionManager.get(request).getMerchantId();
        if (merchantId == null) {
            userType = UserType.sma.getValue();
            JunhaiAssert.isTrue(systemUserDto.getMerchantId() != null, "商户ID不能为空");
            merchantId = systemUserDto.getMerchantId();
        }

        SystemUser systemUser = new SystemUser();
        systemUser.setNickname(systemUserDto.getNickname());
        systemUser.setTelephone(systemUserDto.getTelephone());
        systemUser.setPictureUrl("/assets/img/avatars/Osvaldus-Valutis.jpg");
        systemUser.setRoleIds(systemUserDto.getRoleIds());
        systemUser.setLoginName(systemUserDto.getLoginName());
        systemUser.setPasswd(systemUserDto.getPasswd());
        systemUser.setUserType(userType);
        systemUser.setMerchantId(merchantId);
        systemUser.setState(UserState.normal.getValue());
        systemUserService.insert(systemUser);
        return jsonSuccess();
    }

    @RequestMapping("/notRepeatLoginName")
    @ResponseBody
    public String notRepeatLoginName(String loginName) {
        if (StringUtils.isBlank(loginName)) {
            return validateSuccess();
        }

        SystemUser systemUser = systemUserService.getSystemUserByLoginName(loginName);
        return systemUser == null ? validateSuccess() : validateFailed();
    }

    @RequestMapping("/editUser")
    @ResponseBody
    public String editUser(HttpServletRequest request, SystemUserDto systemUserDto) {
        JunhaiAssert.notNull(systemUserDto.getId(), "用户Id不能为空");
        JunhaiAssert.notBlank(systemUserDto.getNickname(), "用户昵称不能为空不能为空");
        JunhaiAssert.notBlank(systemUserDto.getTelephone(), "手机号码不能为空");

        Integer merchantId = SessionManager.get(request).getMerchantId();
        SystemUser systemUser = systemUserService.read(systemUserDto.getId());
        JunhaiAssert.isTrue(merchantId == null || merchantId.equals(systemUser.getMerchantId()), "id无效");

        // 超级系统管理员和商户户主不可以编辑角色
        Integer userType = systemUser.getUserType();
        if (UserType.sa.getValue().equals(userType) || UserType.ma.getValue().equals(userType)) {
            systemUser.setRoleIds(systemUserDto.getRoleIds());
        }

        systemUser.setNickname(systemUserDto.getNickname());
        systemUser.setTelephone(systemUserDto.getTelephone());
        systemUserService.update(systemUser);

        return jsonSuccess();
    }

    @RequestMapping("/editPwd")
    @ResponseBody
    public String editPwd(HttpServletRequest request, SystemUserDto systemUserDto) {
        JunhaiAssert.notNull(systemUserDto.getId(), "用户ID不能为空");
        JunhaiAssert.notBlank(systemUserDto.getPasswd(), "登陆密码不能为空");

        // 验证用户是否有操作权限
        SystemUser systemUser = systemUserService.read(systemUserDto.getId());
        Integer merchantId = SessionManager.get(request).getMerchantId();
        JunhaiAssert.isTrue(merchantId == null || merchantId.equals(systemUser.getMerchantId()), "id无效");

        // 更新密码
        systemUser.setPasswd(systemUserDto.getPasswd());
        systemUser.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        systemUserService.update(systemUser);

        return jsonSuccess();
    }

    @RequestMapping("/lockUser")
    @ResponseBody
    public String lockUser(HttpServletRequest request, SystemUserDto systemUserDto) {
        JunhaiAssert.notNull(systemUserDto.getId(), "用户ID不能为空");

        // 验证用户是否有操作权限
        SystemUser systemUser = systemUserService.read(systemUserDto.getId());
        Integer merchantId = SessionManager.get(request).getMerchantId();
        JunhaiAssert.isTrue(merchantId == null || merchantId.equals(systemUser.getMerchantId()), "id无效");
        systemUser.setState(UserState.lock.getValue());
        systemUserService.update(systemUser);
        return jsonSuccess();
    }

    @RequestMapping("/unlockUser")
    @ResponseBody
    public String unlockUser(HttpServletRequest request, SystemUserDto systemUserDto) {
        JunhaiAssert.notNull(systemUserDto.getId(), "用户ID不能为空");

        // 验证用户是否有操作权限
        SystemUser systemUser = systemUserService.read(systemUserDto.getId());
        Integer merchantId = SessionManager.get(request).getMerchantId();
        JunhaiAssert.isTrue(merchantId == null || merchantId.equals(systemUser.getMerchantId()), "id无效");
        systemUser.setState(UserState.normal.getValue());
        systemUserService.update(systemUser);
        return jsonSuccess();
    }

    @RequestMapping("/roleList")
    public String roleList(HttpServletRequest request, SystemRoleDto systemRoleDto, ModelMap modelMap) {
        systemRoleDto.setMerchantId(SessionManager.get(request).getMerchantId());
        Integer iCount = systemRoleService.querySystemRoleCount(systemRoleDto);
        Pagination pagination = new Pagination(systemRoleDto.getPageNo(), iCount);

        List<SystemRole> systemRoleList = new ArrayList<SystemRole>();
        if (iCount > 0) {
            systemRoleDto.setOffset(pagination.getOffset());
            systemRoleDto.setPageSize(pagination.getPageSize());
            systemRoleList = systemRoleService.querySystemRoleList(systemRoleDto);

            // 根据商户IDS获取商户列表
            List<Integer> merchantIds = SystemRoleUtil.getMerchantIdList(systemRoleList);
            List<MerchantInfo> merchantList = merchantService.getMerchantListByIds(merchantIds);
            modelMap.put("merchantMap", MerchantUtil.getMerchantMap(merchantList));
        }
        modelMap.put("systemRoleList", systemRoleList);
        modelMap.put("pagination", pagination);
        return "system/roleList";
    }

    @RequestMapping("/saveRole")
    @ResponseBody
    public String saveRole(HttpServletRequest request, SystemRoleDto systemRoleDto) {
        JunhaiAssert.notBlank(systemRoleDto.getRoleName(), "角色名不能为空");

        // 验证用户是否有操作权限
        SystemRole systemRole = new SystemRole();
        if (systemRoleDto.getId() != null) {
            Integer merchantId = SessionManager.get(request).getMerchantId();
            systemRole = systemRoleService.read(systemRoleDto.getId());
            JunhaiAssert.isTrue(merchantId == null || merchantId.equals(systemRole.getMerchantId()), "id无效");
        }

        // 新增或编辑角色
        systemRole.setRoleName(systemRoleDto.getRoleName());
        if (systemRoleDto.getId() == null) {
            systemRole.setRoleType(RoleType.ma.getValue());
            systemRole.setMerchantId(SessionManager.get(request).getMerchantId());
            systemRoleService.insert(systemRole);
        } else {
            systemRoleService.update(systemRole);
        }

        return jsonSuccess();
    }

    @RequestMapping("/toAuthorize")
    public String toAuthorize(HttpServletRequest request, String roleId, ModelMap modelMap) {

        List<SystemAction> authorizedActionList = null;
        if (UserType.ssa.getValue().equals(SessionManager.get(request).getUserType())) {
            authorizedActionList = systemActionService.getAllSystemActionList();
        } else {
            // 获取当前用户角色
            String roleIds = SessionManager.get(request).getRoleIds();

            // 获取当前用户所拥有的action列表
            List<SystemPrivilage> authorizedPrivilageList = null;
            authorizedPrivilageList = systemPrivilageService.getSystemPrivilageListByRoleIds(roleIds);
            List<Integer> authorizedActionIdList = SystemPrivilageUtil.getActionIdList(authorizedPrivilageList);
            authorizedActionList = systemActionService.getSystemActionListByIds(authorizedActionIdList);
        }

        // 获取根(一级菜单)action列表，并按父节点分组
        List<SystemAction> rootSystemActionList = new ArrayList<SystemAction>();
        Map<String, List<SystemAction>> parentSystemActionMap = new HashMap<String, List<SystemAction>>();
        for (SystemAction systemAction : authorizedActionList) {
            if (CommonConstant.ROOT_MENU_ID.equals(systemAction.getParentId())) {
                rootSystemActionList.add(systemAction);
            }

            List<SystemAction> groupList = parentSystemActionMap.get(String.valueOf(systemAction.getParentId()));
            if (groupList == null) {
                groupList = new ArrayList<SystemAction>();
                parentSystemActionMap.put(String.valueOf(systemAction.getParentId()), groupList);
            }
            groupList.add(systemAction);
        }

        // 获取当前角色已分配的权限列表
        List<SystemPrivilage> legalPrivilageList = systemPrivilageService.getSystemPrivilageListByRoleIds(roleId);

        modelMap.put("roleId", roleId);
        modelMap.put("rootSystemActionList", rootSystemActionList);
        modelMap.put("parentSystemActionMap", parentSystemActionMap);
        modelMap.put("legalPrivilageList", legalPrivilageList);
        return "system/authorize";
    }

    @RequestMapping("/saveAuthorize")
    @ResponseBody
    public String saveAuthorize(HttpServletRequest request, SystemPrivilageDto systemPrivilageDto, ModelMap modelMap) {
        JunhaiAssert.notNull(systemPrivilageDto.getRoleId(), "角色ID不能为空");

        // 获取当前用户角色
        String roleIds = SessionManager.get(request).getRoleIds();

        // 获取当前用户所拥有的action列表
        List<SystemPrivilage> authorizedPrivilageList = systemPrivilageService.getSystemPrivilageListByRoleIds(roleIds);
        Map<Integer, SystemPrivilage> authorizedPrivilageMap = null;
        authorizedPrivilageMap = SystemPrivilageUtil.getSystemPrivilageMap(authorizedPrivilageList);

        // 获取全部action
        List<SystemAction> allSystemActionList = systemActionService.getAllSystemActionList();
        Map<String, SystemAction> systemActionMap = SystemActionUtil.getSystemActionMap(allSystemActionList);

        // 获取出需要并且可以授权的actionId
        Set<Integer> actionIdSet = new HashSet<Integer>();
        Integer userType = SessionManager.get(request).getUserType();
        if (StringUtils.isNotBlank(systemPrivilageDto.getActionIds())) {
            String[] arrActionId = systemPrivilageDto.getActionIds().split(",");
            for (String strActionId : arrActionId) {
                Integer actionId = Integer.parseInt(strActionId);
                if (!UserType.ssa.getValue().equals(userType) && authorizedPrivilageMap.get(actionId) == null) {
                    continue;
                }

                // 添加操作路径树-会自动对父节点授权
                List<SystemAction> systemActionList = SystemActionUtil.getCurAction2RootList(actionId, systemActionMap);
                for (SystemAction systemAction : systemActionList) {
                    actionIdSet.add(systemAction.getId());
                }
            }
        }

        // 保存权限数据
        systemPrivilageService.saveSystemPrivilage(systemPrivilageDto.getRoleId(), actionIdSet);
        return jsonSuccess();
    }

    @RequestMapping("/operateLogList")
    public String operateLogList(HttpServletRequest request, SystemOperateLogDto systemOperateLogDto, ModelMap modelMap) {
        Integer iCount = systemOperateLogService.querySystemOperateLogCount(systemOperateLogDto);
        Pagination pagination = new Pagination(systemOperateLogDto.getPageNo(), iCount);

        List<SystemOperateLog> systemOperateLogList = new ArrayList<SystemOperateLog>();
        if (iCount > 0) {
            systemOperateLogDto.setOffset(pagination.getOffset());
            systemOperateLogDto.setPageSize(pagination.getPageSize());
            systemOperateLogList = systemOperateLogService.querySystemOperateLogList(systemOperateLogDto);

            List<Integer> merchantIds = new ArrayList<Integer>();
            List<Integer> userIds = new ArrayList<Integer>();
            for (SystemOperateLog systemOperateLog : systemOperateLogList) {
                userIds.add(systemOperateLog.getUserId());
                if (systemOperateLog.getMerchantId() != null) {
                    merchantIds.add(systemOperateLog.getMerchantId());
                }
            }

            // 获取用户映射关系
            List<SystemUser> systemUserList = systemUserService.getSystemUserListByUserIds(userIds);
            modelMap.put("userMap", SystemUserUtil.getSystemUserMap(systemUserList));

            // 获取商户映射关系
            List<MerchantInfo> merchantList = merchantService.getMerchantListByIds(merchantIds);
            modelMap.put("merchantMap", MerchantUtil.getMerchantMap(merchantList));

        }

        modelMap.put("systemOperateLogDto", systemOperateLogDto);
        modelMap.put("systemOperateLogList", systemOperateLogList);
        modelMap.put("pagination", pagination);
        return "system/operateLogList";
    }
}
