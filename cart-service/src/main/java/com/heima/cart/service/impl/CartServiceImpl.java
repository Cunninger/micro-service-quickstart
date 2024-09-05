package com.heima.cart.service.impl;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.heima.cart.domain.dto.CartFormDTO;

import com.heima.cart.domain.po.Cart;
import com.heima.cart.domain.vo.CartVO;
import com.heima.cart.mapper.CartMapper;
import com.heima.cart.service.ICartService;
import com.heima.hmapi.client.ItemClient;
import com.heima.hmapi.dto.ItemDTO;
import com.hmall.common.exception.BizIllegalException;
import com.hmall.common.utils.BeanUtils;
import com.hmall.common.utils.CollUtils;
import com.hmall.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.stereotype.Service;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 * 订单详情表 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2023-05-05
 */
@Service
@RequiredArgsConstructor//作用    生成一个包含常量，所有属性以及get和set方法的构造方法
public class CartServiceImpl extends ServiceImpl<CartMapper, Cart> implements ICartService {


    // 修改后
//    private final RestTemplate restTemplate;
//    private final DiscoveryClient discoveryClient;

    private final ItemClient itemClient;

    @Override
    public void addItem2Cart(CartFormDTO cartFormDTO) {
        // 1.获取登录用户
        Long userId = UserContext.getUser();

        // 2.判断是否已经存在
        if (checkItemExists(cartFormDTO.getItemId(), userId)) {
            // 2.1.存在，则更新数量
            baseMapper.updateNum(cartFormDTO.getItemId(), userId);
            return;
        }
        // 2.2.不存在，判断是否超过购物车数量
        checkCartsFull(userId);

        // 3.新增购物车条目
        // 3.1.转换PO
        Cart cart = BeanUtils.copyBean(cartFormDTO, Cart.class);
        // 3.2.保存当前用户
        cart.setUserId(userId);
        // 3.3.保存到数据库
        save(cart);
    }

    @Override
    public List<CartVO> queryMyCarts() {
        // 1.查询我的购物车列表
        List<Cart> carts = lambdaQuery().eq(Cart::getUserId, 1L /*UserContext.getUser()*/).list();
        if (CollUtils.isEmpty(carts)) {
            return CollUtils.emptyList();
        }

        // 2.转换VO
        List<CartVO> vos = BeanUtils.copyList(carts, CartVO.class);

        // 3.处理VO中的商品信息
        handleCartItems(vos);

        // 4.返回
        return vos;
    }
    private AtomicInteger position = new AtomicInteger(0);

    private ServiceInstance getRoundRobinInstance(List<ServiceInstance> instances) {
        // 获取当前索引值，并且对实例数取模，防止超出列表范围
        int pos = position.getAndUpdate(current -> (current + 1) % instances.size());
        return instances.get(pos);
    }

    private void handleCartItems(List<CartVO> vos) {
        //TODO 1.获取商品id
        Set<Long> itemIds = vos.stream().map(CartVO::getItemId).collect(Collectors.toSet());
//        List<ServiceInstance> instances = discoveryClient.getInstances("item-service");
//        if (CollUtil.isEmpty(instances)) {
//            return;
//        }
//        // 添加轮询算法
//        ServiceInstance instance = getRoundRobinInstance(instances);
//
//        ResponseEntity<List<ItemDTO>> response = restTemplate.exchange(
//                instance.getUri()+"/items?ids={ids}",
//                HttpMethod.GET,
//                null,
//                new ParameterizedTypeReference<List<ItemDTO>>() {
//                    //ParameterizedTypeReference 用于获取泛型类型
//                }, Map.of("ids", CollUtil.join(itemIds, ","))
//        );
//        if (!response.getStatusCode().is2xxSuccessful()) {
//            return;
//        }
//        List<ItemDTO> items = response.getBody();

        List<ItemDTO> items = itemClient.queryItemByIds(itemIds);
        if (CollUtils.isEmpty(items)) {
            return;
        }
        // 3.转为 id 到 item的map
        Map<Long, ItemDTO> itemMap = items.stream().collect(Collectors.toMap(ItemDTO::getId, Function.identity()));
        // 4.写入vo
        for (CartVO v : vos) {
            ItemDTO item = itemMap.get(v.getItemId());
            if (item == null) {
                continue;
            }
            v.setNewPrice(item.getPrice());
            v.setStatus(item.getStatus());
            v.setStock(item.getStock());
        }
    }

    @Override
    public void removeByItemIds(Collection<Long> itemIds) {
        // 1.构建删除条件，userId和itemId
        QueryWrapper<Cart> queryWrapper = new QueryWrapper<Cart>();
        queryWrapper.lambda()
                .eq(Cart::getUserId, UserContext.getUser())
                .in(Cart::getItemId, itemIds);
        // 2.删除
        remove(queryWrapper);
    }

    private void checkCartsFull(Long userId) {
        int count = lambdaQuery().eq(Cart::getUserId, userId).count();
        if (count >= 10) {
            throw new BizIllegalException(StrUtil.format("用户购物车课程不能超过{}", 10));
        }
    }

    private boolean checkItemExists(Long itemId, Long userId) {
        int count = lambdaQuery()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getItemId, itemId)
                .count();
        return count > 0;
    }
}
