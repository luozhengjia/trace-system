package com.ejunhai.trace.system.dao;

import java.util.List;

import com.ejunhai.trace.system.dto.SystemUserDto;
import com.ejunhai.trace.system.model.SystemUser;

/**
 * SystemUserMapper 系统用户表
 * 
 * @author parcel
 * 
 * @date 2014-12-10 21:22:36
 * 
 */
public interface SystemUserMapper {

    /**
     * 根据Id获取SystemUser
     * 
     * @param id
     * @return
     */
    public SystemUser read(Integer id);

    /**
     * 新增SystemUser
     * 
     * @param systemUser
     */
    public void insert(SystemUser systemUser);

    /**
     * 更新SystemUser
     * 
     * @param systemUser
     */
    public void update(SystemUser systemUser);

    /**
     * 删除SystemUser
     * 
     * @param id
     */
    public void delete(Integer id);

    /**
     * 查询SystemUser数量
     * 
     * @param systemUserDto
     * @return
     */
    public Integer querySystemUserCount(SystemUserDto systemUserDto);

    /**
     * 查询SystemUser列表
     * 
     * @param systemUserDto
     * @return
     */
    public List<SystemUser> querySystemUserList(SystemUserDto systemUserDto);

    /**
     * 根据登录名获取系统用户
     * 
     * @param loginName
     * @return
     */
    public SystemUser getSystemUserByLoginName(String loginName);

    public List<SystemUser> getSystemUserListByUserIds(List<Integer> userIds);

}
