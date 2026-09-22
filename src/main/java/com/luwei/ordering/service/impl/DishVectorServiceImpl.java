package com.luwei.ordering.service.impl;

import com.luwei.ordering.common.enums.Category;
import com.luwei.ordering.common.enums.SpicyLevel;
import com.luwei.ordering.dto.response.DishMatch;
import com.luwei.ordering.dto.response.QueryCondition;
import com.luwei.ordering.entity.DishEntity;
import com.luwei.ordering.mapper.DishMapper;
import com.luwei.ordering.service.DishVectorService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;


import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Slf4j
@Service
@RequiredArgsConstructor
public class DishVectorServiceImpl implements DishVectorService {
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final DishMapper dishMapper;

    //TODO 后续可以加一个@scheduled的定时操作来实现失败之后，不用人为的重置向量库
    @Override
    public void syncDish(DishEntity entity) {
        log.debug("向量库同步,dishNumber:{},dishName:{}" ,
                  entity.getDishNumber() , entity.getDishName());
        //做一次，向量库中删除旧向量的操作
        removeDish(entity.getDishNumber());

        //调用私有方法，进行转化entity为textSegment操作
        TextSegment segment = buildSegment(entity);

        //使用EmbeddingModel 向量化数据
        Embedding embedding = embeddingModel.embed(segment).content();

        //存入EmbeddingStore(向量库)中
        embeddingStore.add(embedding , segment);
    }

    @Override
    public void removeDish(Long dishNumber) {
        log.info("向量库删除,dishNumber:{}" , dishNumber);
        embeddingStore.removeAll(metadataKey("dishNumber").isEqualTo(dishNumber));
    }

    @Override
    public void resyncDish() {
        //数据库中取出所有上架的菜品
        List<DishEntity> entities = dishMapper.findAvailableAll();
        //清空向量库
        embeddingStore.removeAll();
        //把传入的entity对象全部转为textSegment，逐个组装
        List<TextSegment> segments = entities.stream()
                                     .map(this::buildSegment)
                                     .toList();
        //向量化所有的文本对象
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        //把向量化后的对象存入向量库中
        embeddingStore.addAll(embeddings , segments);
        log.info("全量重建完成, 共{}道菜", segments.size());
    }


    @Override
    public List<DishMatch> searchDishes(String query, int topK, QueryCondition condition) {
        //先向量化查询query
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        //做一步转化，加入topK变成EmbeddingSearchRequest对象
        EmbeddingSearchRequest.EmbeddingSearchRequestBuilder builder = EmbeddingSearchRequest
                                                                       .builder()
                                                                       .queryEmbedding(queryEmbedding)
                                                                       .maxResults(topK);
        //过滤出来的条件,创建一个filter
        Filter filter = buildFilter(condition);
        if (filter != null){
            builder.filter(filter);
            log.debug("混合检索生效, filter:{}", filter);
        }

        //向量库中查询结果并返回
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(builder.build());

        result.matches().forEach(m ->
                log.debug("命中 dishNumber:{}, score:{}",
                        m.embedded().metadata().getLong("dishNumber"), m.score()));

        //过滤相关分数低的，然后只取出dishNumber合成一个List返回
        return result.matches()
                     .stream()
                     .map(m -> new DishMatch(m.embedded().metadata().getLong("dishNumber") , m.score()))
                     .toList();
    }


    private TextSegment buildSegment(DishEntity entity)
    {
        //做一个Introduction判空操作，防止污染数据
        String introduction = StringUtils.hasText(entity.getDishIntroduction()) ?
                entity.getDishIntroduction() : "" ;
        //组装文本 文本信息
        String text = entity.getDishName() + " " +
                entity.getCategory().getCode() + " " +
                introduction + " " +
                entity.getPrice();
        //组装文本 元数据metadata和文本信息 组装为textSegment
        Metadata metadata =  Metadata.from("category" , entity.getCategory().getCode())
                .put("dishNumber" , entity.getDishNumber())
                .put("price" , entity.getPrice().toString())
                .put("spicyLevel" , entity.getSpicyLevel().getCode());

        return TextSegment.from(text , metadata);
    }



    private Filter buildFilter(QueryCondition condition){
        if(condition == null) return null;
        List<Filter> filters = new ArrayList<>();

        if(condition.spicyLevels() != null && !condition.spicyLevels().isEmpty()){
            List<String> codes = condition.spicyLevels()
                                         .stream()
                                         .map(SpicyLevel :: getCode)
                                         .toList();
            filters.add(metadataKey("spicyLevel").isIn(codes));
        }

        if(condition.excludeCategories() != null && !condition.excludeCategories().isEmpty()){
            List<String> codes = condition.excludeCategories()
                                          .stream()
                                          .map(Category :: getCode)
                                          .toList();
            filters.add(metadataKey("category").isNotIn(codes));
        }
        return filters.stream().reduce((f1 , f2) -> f1.and(f2)).orElse(null);
    }
}
