package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {



    @Resource
    private IUserService userService;
    @Resource
    private IFollowService followService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryBlogById(Long id) {
        //查询blog
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("博客不存在");
        }
        queryBlogUser(blog);
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            queryBlogUser(blog);
            isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        //1.判断当前用户是否已点赞
        Long userId = UserHolder.getUser().getId();
        String key = BLOG_LIKED_KEY + id;
        //查看redis中是否已经有数据
//        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
        Double score = stringRedisTemplate.opsForZSet().score(key,userId.toString());
        //下面执行的双写，后续查询只需查询redis
//        if(BooleanUtil.isFalse(isMember)){
        if (score==null) {
            //2.未点赞：数据库赞+1
            boolean isSuccess = update().setSql("liked = liked +  1").eq("id", id).update();
            //3.用户信息保存到Redis的点赞set
            if(isSuccess){
//                stringRedisTemplate.opsForSet().add(key,userId.toString());
                stringRedisTemplate.opsForZSet().add(key,userId.toString(),System.currentTimeMillis());
            }
        }
        else{
            //4.已点赞:数据库-1
            boolean isSuccess = update().setSql("liked = liked -  1").eq("id", id).update();
            //5.把用户信息从Redis的点赞set移除
            if(isSuccess){
//                stringRedisTemplate.opsForSet().remove(key,userId.toString());
                stringRedisTemplate.opsForZSet().remove(key,userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikesById(Long id) {
        String key = BLOG_LIKED_KEY + id;
        //查询top5的点赞用户
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 6);
        if (top5==null||top5.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //解析出用户的id
        List<Long> userIds = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String join = StrUtil.join(",", userIds);
        //根据id查询用户，数据库不是根据查询的顺序返回，我们需要指定顺序，将join 作为数据传入
        List<UserDTO> userDTOS = userService.lambdaQuery()
                .in(User::getId, userIds)
                .last("order by field(id," + join + ")")
                .list()
                .stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        //返回结果
        return  Result.ok(userDTOS);
    }
/**
 * 实现滚动分页查询自己的关注
 */

//    @Override
//    public Result queryBlogOfFollow(Long max, Integer offset) {
////获取当前用户
//        UserDTO user =UserHolder.getUser();
//        //查询当前用户收件箱 zrevrangebyscore key max min limit offset count
//        String key = "feed:"+user.getId();
//        //
//    }


    private void queryBlogUser(Blog blog) {

        //访问博客作者，设置昵称和图标
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    /*
    * 判断是否是当前用户点赞的
    * */
    private void isBlogLiked(Blog blog) {
        //获取当前登陆用户
        UserDTO user = UserHolder.getUser();
        if (user==null){
            return;
        }

        Long userId = user.getId();
        //判断当前用户是否点赞过这篇笔记
        String key = BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        //若非空，则设置为喜欢状态
        blog.setIsLike(score!=null);
//        Boolean isMenber = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
//        blog.setIsLike(BooleanUtil.isTrue(isMenber));
    }
}
