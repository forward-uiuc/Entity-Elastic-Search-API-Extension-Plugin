package org.forward.entitysearch.esdocumentsearch;

import static org.forward.entitysearch.esdocumentsearch.LoggerUtils.emitErrorResponse;
import static org.elasticsearch.action.ValidateActions.addValidationError;
import static org.elasticsearch.rest.RestRequest.Method.POST;
import static org.elasticsearch.rest.RestRequest.Method.GET;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.CopyOnWriteArraySet;

import org.elasticsearch.action.Action;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionRequestBuilder;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.TransportSearchAction;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.TransportAction;
import org.elasticsearch.client.ElasticsearchClient;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.action.search.RestSearchAction;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

/**
 * Perform clustering of search results.
 */
public class ClusteringAction extends
        Action<ClusteringAction.ClusteringActionRequest, ClusteringAction.ClusteringActionResponse, ClusteringAction.ClusteringActionRequestBuilder> {
    /* Action name. */
    public static final String NAME = "esdocumentsearch";
    public static ArrayList<String> category = new ArrayList<String>();

    /* Reusable singleton. */
    public static final ClusteringAction INSTANCE = new ClusteringAction();

    private ClusteringAction() {
        super(NAME);
    }

    @Override
    public ClusteringActionResponse newResponse() {
        return new ClusteringActionResponse();
    }

    @Override
    public ClusteringActionRequestBuilder newRequestBuilder(ElasticsearchClient client) {
        return new ClusteringActionRequestBuilder(client);
    }

    /**
     * An {@link ActionRequest} for {@link ClusteringAction}.
     */
    public static class ClusteringActionRequest extends ActionRequest {
        private SearchRequest searchRequest;
        private String algorithm;
        private int maxHits = Integer.MAX_VALUE;
        private Map<String, Object> attributes;

        /**
         * Set the {@link SearchRequest} to use for fetching documents to be clustered.
         * The search request must fetch enough documents for clustering to make sense
         * (set <code>size</code> appropriately).
         *
         * @param searchRequest search request to set
         * @return same builder instance
         */
        public ClusteringActionRequest setSearchRequest(SearchRequest searchRequest) {
            this.searchRequest = searchRequest;
            return this;
        }

        /**
         * @see #setSearchRequest(SearchRequest)
         */
        public ClusteringActionRequest setSearchRequest(SearchRequestBuilder builder) {
            return setSearchRequest(builder.request());
        }

        public SearchRequest getSearchRequest() {
            return searchRequest;
        }

        class QueryProperties {
            public boolean inOrder;
            public int slop;
            public double boost;

            QueryProperties(boolean inOrder, int slop, double boost) {
                this.inOrder = inOrder;
                this.slop = slop;
                this.boost = boost;
            }
        }

        /**
         * Parses some {@link org.elasticsearch.common.xcontent.XContent} and fills in
         * the request.
         */
        @SuppressWarnings("unchecked")
        public void source(BytesReference source, NamedXContentRegistry xContentRegistry) {
            if (source == null || source.length() == 0) {
                return;
            }

            try (XContentParser parser = XContentFactory.xContent(source).createParser(xContentRegistry, source)) {
                // TODO: we should avoid reparsing search_request here
                // but it's terribly difficult to slice the underlying byte
                // buffer to get just the search request.
                Map<String, Object> asMap = parser.mapOrdered();
                Map<String, Object> searchRequestMap = (Map<String, Object>) asMap.get("search_request");
                final String DELIMITER = " ";
                if (searchRequestMap != null) {
                    if (this.searchRequest == null) {
                        searchRequest = new SearchRequest();
                    }
                    // String[] tokens = ((String)searchRequestMap.get("query")).split(DELIMITER);
                    StringTokenizer tokens = new StringTokenizer((String) searchRequestMap.get("query"), DELIMITER);
//					boolean flag = false;
//					if (searchRequestMap.get("type") != null
//							&& ((String) searchRequestMap.get("type")).equals("e_document")) {
//						flag = true;
//						searchRequestMap.remove("type");
//					}
                    // HashMap<String, Object> query = (HashMap<String,
                    // Object>)searchRequestMap.get("query");
                    List<HashMap<String, Object>> spanNearElements = new ArrayList<>();
                    while (tokens.hasMoreTokens()) {
                        String token = tokens.nextToken();
                        if (token.charAt(0) == '@') {
                            HashMap<String, Object> subQuery = createOperation(tokens, token);
                            spanNearElements.add(subQuery);
                        }
                    }
//					tokens = new StringTokenizer((String) searchRequestMap.get("query"), DELIMITER);
//					spanNearElements = getAllTokens(tokens);


//                    HashMap<String, Object> query = createSpanNearQuery(spanNearElements, false, Integer.MAX_VALUE, 1);
                    HashMap<String, Object> query = createBoolShouldMatchQuery(spanNearElements, -1);
                    searchRequestMap.put("query", query);
                    searchRequestMap.put("size", 1000);
                    ArrayList<String> shownFields = new ArrayList<>();
                    shownFields.add("title");
                    shownFields.add("url");
                    shownFields.add("text");
                    searchRequestMap.put("_source", shownFields);
                    System.out.println(searchRequestMap);
                    XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON).map(searchRequestMap);
                    XContentParser searchXParser = XContentFactory.xContent(XContentType.JSON)
                            .createParser(xContentRegistry, builder.bytes());
                    QueryParseContext parseContext = new QueryParseContext(searchXParser);
                    SearchSourceBuilder searchSourceBuilder = SearchSourceBuilder.fromXContent(parseContext);
                    searchRequest.source(searchSourceBuilder);
                }
            } catch (Exception e) {
                String sSource = "_na_";
                try {
                    sSource = XContentHelper.convertToJson(source, false, false, XContentFactory.xContentType(source));
                } catch (Throwable e1) {
                    // ignore
                }
                e.printStackTrace();
                throw new org.forward.entitysearch.esdocumentsearch.ClusteringException("Failed to parse source [" + sSource + "]" + e, e);
            }
        }

        private HashMap<String, Object> createOperation(StringTokenizer tokens, String token) {
            HashMap<String, Object> subQuery = null;
            switch (token) {
                case "@near":
                    subQuery = createNearOperation(tokens, "_entity_");
                    break;
                case "@near_x":
                    subQuery = createNearOperation(tokens, "_xpos_entity_");
                    break;
                case "@near_y":
                    subQuery = createNearOperation(tokens, "_ypos_entity_");
                    break;
                case "@contains":
                    subQuery = createContainsOperation(tokens);
                    break;
                case "@or":
                    subQuery = createOrOperation(tokens, "_entity_");
                    break;
                case "@or_x":
                    subQuery = createOrOperation(tokens, "_xpos_entity_");
                    break;
                case "@or_y":
                    subQuery = createOrOperation(tokens, "_ypos_entity_");
                    break;
                default:
                    System.err.print("Operation is not supported!");
            }
            return subQuery;
        }

        private HashMap<String, Object> createContainsOperation(StringTokenizer tokens) {
            String token;
            HashMap<String, Object> subQuery;
            token = tokens.nextToken();
            if (!token.equalsIgnoreCase("(")) {
                System.out.println("Expected ( after @contains here!!!! but get the following instead " + token);
            }
            List<HashMap<String, Object>> tmp = getAllTokensWithinBracket(tokens, "_entity_", "");
            subQuery = createSpanNearQuery(tmp, false, Integer.MAX_VALUE, 1);
            return subQuery;
        }

        private HashMap<String, Object> createNearOperation(StringTokenizer tokens, String type) {
            String token;
            HashMap<String, Object> subQuery;
            token = tokens.nextToken();
            QueryProperties properties = new QueryProperties(false, 10, 1);
            if (token.equalsIgnoreCase("[")) {
                properties = getPropertiesWithinBracket(tokens);
            }
            List<HashMap<String, Object>> tmp = getAllTokensWithinBracket(tokens, type, "");
            subQuery = createSpanNearQuery(tmp, properties.inOrder, properties.slop, properties.boost);
            return subQuery;
        }

        private HashMap<String, Object> createOrOperation(StringTokenizer tokens, String type) {
            HashMap<String, Object> subQuery;
            tokens.nextToken();
            List<HashMap<String, Object>> tmp = getAllTokensWithinBracket(tokens, type, "");
            subQuery = createSpanOrQuery(tmp);
            return subQuery;
        }

//		@SuppressWarnings("unchecked")
//		private List<HashMap<String, Object>> getAllTokens(StringTokenizer tokens) {
//			List<HashMap<String, Object>> spanNearElements = new ArrayList<>();
//			while (tokens.hasMoreTokens()) {
//				String token = tokens.nextToken();
//				if (token.charAt(0) == '#') {
//					token = token.substring(1);
//					HashMap<String, Object> hashMapElement = getSpanElementForEntity(token, "_entity_", "_begin");
//					spanNearElements.add(hashMapElement);
//				} else {
//					HashMap<String, Object> hashMapElement = getSpanElementForKeyword(token);
//					spanNearElements.add(hashMapElement);
//				}
//			}
//			return spanNearElements;
//		}

        @SuppressWarnings("unchecked")
        private List<HashMap<String, Object>> getAllTokensWithinBracket(StringTokenizer tokens, String type, String suffix) {
            List<HashMap<String, Object>> spanNearElements = new ArrayList<>();
            while (tokens.hasMoreTokens()) {
                String token = tokens.nextToken();
                if (token.equalsIgnoreCase(")")) {
                    // tokens.nextToken();
                    break;
                }
                if (token.charAt(0) == '@') {
                    HashMap<String, Object> subQuery = createOperation(tokens, token);
                    spanNearElements.add(subQuery);
                } else if (token.charAt(0) == '#') {
                    HashMap<String, Object> hashMapElement = getSpanElementForEntity(token, type, suffix);
                    spanNearElements.add(hashMapElement);
                } else {
                    HashMap<String, Object> hashMapElement = getSpanElementForKeyword(token);
                    spanNearElements.add(hashMapElement);
                }
            }
            return spanNearElements;
        }

        @SuppressWarnings("unchecked")
        private QueryProperties getPropertiesWithinBracket(StringTokenizer tokens) {
            ArrayList<String> properties = new ArrayList<>();
            while (tokens.hasMoreTokens()) {
                String token = tokens.nextToken();
                if (token.equalsIgnoreCase("]")) {
                    tokens.nextToken();
                    break;
                } else {
                    properties.add(token);
                }
            }
            boolean inOrder = false;
            int slop = 10;
            double boost = 1;
            if (properties.size() > 0) {
                inOrder = Boolean.parseBoolean(properties.get(0));
            }
            if (properties.size() > 1) {
                slop = Integer.parseInt(properties.get(1));
            }
            if (properties.size() > 2) {
                boost = Double.parseDouble(properties.get(2));
            }
            if (properties.size() == 0) {
                System.out.println("Empty properties detected");
            }
            System.out.println("" + inOrder + " " + slop + " " + boost);
            System.out.println(properties.size());
            return new QueryProperties(inOrder, slop, boost);
        }

        @SuppressWarnings("unchecked")
        private HashMap<String, Object> createSpanNearQuery(List<HashMap<String, Object>> spanNearElements,
                                                            boolean inOrder, int slop, double boost) {
            HashMap<String, Object> query = new HashMap<String, Object>();
            query.put("span_near", new HashMap<String, Object>());
            ((HashMap<String, Object>) query.get("span_near")).put("clauses", spanNearElements);
            ((HashMap<String, Object>) query.get("span_near")).put("slop", slop);
            ((HashMap<String, Object>) query.get("span_near")).put("in_order", inOrder);
            ((HashMap<String, Object>) query.get("span_near")).put("boost", boost);
            return query;
        }

        @SuppressWarnings("unchecked")
        private HashMap<String, Object> createBoolShouldMatchQuery(List<HashMap<String, Object>> elements,
                                                             int minShouldMatch) {
            HashMap<String, Object> query = new HashMap<String, Object>();
            query.put("bool", new HashMap<String, Object>());
            ((HashMap<String, Object>) query.get("bool")).put("should", elements);
            if (minShouldMatch > 0) {
                ((HashMap<String, Object>) query.get("bool")).put("minimum_should_match", minShouldMatch);
            }
            return query;
        }

        @SuppressWarnings("unchecked")
        private HashMap<String, Object> createSpanOrQuery(List<HashMap<String, Object>> elements) {
            HashMap<String, Object> query = new HashMap<String, Object>();
            query.put("span_or", new HashMap<String, Object>());
            ((HashMap<String, Object>) query.get("span_or")).put("clauses", elements);
            return query;
        }

        @SuppressWarnings("unchecked")
        private HashMap<String, Object> getSpanElementForKeyword(String token) {
            HashMap<String, Object> hashMapElement = new HashMap<String, Object>();
            hashMapElement.put("span_term", new HashMap<String, Object>());
            HashMap<String, Object> spanTerm = (HashMap<String, Object>) (hashMapElement.get("span_term"));
            spanTerm.put("text", token);
            return hashMapElement;
        }

        @SuppressWarnings("unchecked")
        private HashMap<String, Object> getSpanElementForEntity(String token, String type, String suffix) {
            HashMap<String, Object> hashMapElement = new HashMap<String, Object>();
            hashMapElement.put("field_masking_span", new HashMap<String, Object>());
            HashMap<String, Object> fieldMaskingSpan = (HashMap<String, Object>) (hashMapElement
                    .get("field_masking_span"));
            fieldMaskingSpan.put("query", new HashMap<String, Object>());
            HashMap<String, Object> queryH = (HashMap<String, Object>) (fieldMaskingSpan.get("query"));
            queryH.put("span_term", new HashMap<String, Object>());
            HashMap<String, Object> spanTerm = (HashMap<String, Object>) (queryH.get("span_term"));
            switch (type) {
                case "_entity_":
                    spanTerm.put(FieldDictionary.getInstance().getEntity(token).getKey(), FieldDictionary.getInstance().getEntity(token).getValue());
                    break;
                case "_xpos_entity_":
                    spanTerm.put(FieldDictionary.getInstance().getLayoutX(token).getKey(), FieldDictionary.getInstance().getLayoutX(token).getValue());
                    break;
                case "_ypos_entity_":
                    spanTerm.put(FieldDictionary.getInstance().getLayoutY(token).getKey(), FieldDictionary.getInstance().getLayoutY(token).getValue());
                    break;
                default:
                    System.out.println("SOMETHING WRONG HERE!!!!!");
            }
            fieldMaskingSpan.put("field", "text");
            return hashMapElement;
        }

        @Override
        public ActionRequestValidationException validate() {
            ActionRequestValidationException validationException = null;
            if (searchRequest == null) {
                validationException = addValidationError("No delegate search request", validationException);
            }

            ActionRequestValidationException ex = searchRequest.validate();
            if (ex != null) {
                if (validationException == null) {
                    validationException = new ActionRequestValidationException();
                }
                validationException.addValidationErrors(ex.validationErrors());
            }

            return validationException;
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            assert searchRequest != null;
            this.searchRequest.writeTo(out);
            out.writeOptionalString(algorithm);
            out.writeInt(maxHits);

            boolean hasAttributes = (attributes != null);
            out.writeBoolean(hasAttributes);
            if (hasAttributes) {
                out.writeMap(attributes);
            }
        }

        @Override
        public void readFrom(StreamInput in) throws IOException {
            SearchRequest searchRequest = new SearchRequest();
            searchRequest.readFrom(in);

            this.searchRequest = searchRequest;
            this.algorithm = in.readOptionalString();
            this.maxHits = in.readInt();

            boolean hasAttributes = in.readBoolean();
            if (hasAttributes) {
                attributes = in.readMap();
            }
        }
    }

    /**
     * An {@link ActionRequestBuilder} for {@link ClusteringAction}.
     */
    public static class ClusteringActionRequestBuilder extends
            ActionRequestBuilder<ClusteringActionRequest, ClusteringActionResponse, ClusteringActionRequestBuilder> {

        public ClusteringActionRequestBuilder(ElasticsearchClient client) {
            super(client, ClusteringAction.INSTANCE, new ClusteringActionRequest());
        }

        public ClusteringActionRequestBuilder setSearchRequest(SearchRequestBuilder builder) {
            super.request.setSearchRequest(builder);
            return this;
        }

        public ClusteringActionRequestBuilder setSearchRequest(SearchRequest searchRequest) {
            super.request.setSearchRequest(searchRequest);
            return this;
        }

        public ClusteringActionRequestBuilder setSource(BytesReference content,
                                                        NamedXContentRegistry xContentRegistry) {
            super.request.source(content, xContentRegistry);
            return this;
        }
    }

    /**
     * An {@link ActionResponse} for {@link ClusteringAction}.
     */
    public static class ClusteringActionResponse extends ActionResponse implements ToXContent {
        /**
         * Clustering-related response fields.
         */
        static final class Fields {
            static final String SEARCH_RESPONSE = "search_response";
            static final String CLUSTERS = "clusters";

            // from SearchResponse
            static final String _SCROLL_ID = "_scroll_id";
            static final String _SHARDS = "_shards";
            static final String TOTAL = "total";
            static final String SUCCESSFUL = "successful";
            static final String FAILED = "failed";
            static final String FAILURES = "failures";
            static final String STATUS = "status";
            static final String INDEX = "index";
            static final String SHARD = "shard";
            static final String REASON = "reason";
            static final String TOOK = "took";
            static final String TIMED_OUT = "timed_out";
        }

        private SearchResponse searchResponse;

        ClusteringActionResponse() {
        }

        public ClusteringActionResponse(SearchResponse searchResponse) {
            this.searchResponse = org.forward.entitysearch.esdocumentsearch.Preconditions.checkNotNull(searchResponse);
        }

        public SearchResponse getSearchResponse() {
            return searchResponse;
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            if (searchResponse != null) {
                searchResponse.innerToXContent(builder, ToXContent.EMPTY_PARAMS);
            }
            // System.out.println("type: "+type);
            // SearchHit[] hits = searchResponse.getHits().getHits();
            //
            //
            //
            //
            // HashMap<String, List<SearchHit>> hm = new HashMap<>();
            // for(SearchHit hit : hits) {
            // HashMap<String, ArrayList<String>> hashMapCategory = new HashMap<String,
            // ArrayList<String>> ();
            // String text=(String)hit.sourceAsMap().get("text");
            // for(String s : category)
            // {
            //
            // ArrayList<String> arr = new ArrayList<String> ();
            // arr.add((String)hit.sourceAsMap().get("_entity_"+s+"_begin"));
            // arr.add((String)hit.sourceAsMap().get("_entity_"+s+"_end"));
            // hashMapCategory.put(s,arr);
            // }
            // int begin = -1;
            // int end = -1;
            // int i = 0;
            // String [] textContent=text.split(" ");
            // StringBuffer re = new StringBuffer();
            // for(String k: hashMapCategory.keySet())
            // {
            // //System.out.println("_entity_"+k+"_begin");
            // String strb = (String)hit.sourceAsMap().get("_entity_"+k+"_begin");
            // String stre = (String)hit.sourceAsMap().get("_entity_"+k+"_end");
            // for(i=0; i< strb.split(" ").length; i++)
            // {
            // if(strb.split(" ")[i].startsWith("oentityo"))
            // begin = i;
            // }
            // for(i=0; i<stre.split(" ").length; i++)
            // {
            // if(stre.split(" ")[i].startsWith("oentityo"))
            // end = i;
            // }
            // StringBuffer sb=new StringBuffer();
            // for(i=begin; i<=end; i++)
            // {
            // if(begin==end)
            // {
            // sb.append(textContent[i]);
            // }
            // else
            // {
            // if(sb.length()==0)
            // {
            // sb.append(textContent[i]);
            // }
            // else
            // {
            // sb.append(" ").append(textContent[i]);
            // }
            //
            // }
            // }
            // re.append(sb.toString());
            // }
            // String result = re.toString();
            // String entityContent =result;
            // //System.out.println("EntityContent: "+entityContent);
            //
            // List<SearchHit> ls = hm.getOrDefault(entityContent, new ArrayList<SearchHit>
            // ());
            // ls.add(hit);
            // hm.put(entityContent, ls);
            // }

            List<List<String>> list = new ArrayList<>();
            // for(String name : hm.keySet()) {
            // List<String> cur = new ArrayList<>();
            // List<SearchHit> hl = hm.get(name);
            // cur.add(name);
            // for(SearchHit h: hl)
            // cur.add(String.valueOf(h.getId()));
            // list.add(cur);
            // }
            //
            // Collections.sort(list, new Comparator<List<String>>(){
            // public int compare(List<String> a, List<String> b) {
            // return b.size() - a.size();
            // }
            // });

            builder.startArray(Fields.CLUSTERS);

            for (List<String> ls : list) {
                builder.startObject();
                builder.field("name", ls.get(0));
                builder.startArray("document");
                for (int i = 1; i < ls.size(); i++) {
                    builder.startObject();
                    builder.field("id", ls.get(i));
                    builder.endObject();
                }
                builder.endArray();
                builder.endObject();
            }

            builder.endArray();
            return builder;
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);

            boolean hasSearchResponse = searchResponse != null;
            out.writeBoolean(hasSearchResponse);
            if (hasSearchResponse) {
                this.searchResponse.writeTo(out);
            }
        }

        @Override
        public void readFrom(StreamInput in) throws IOException {
            super.readFrom(in);

            boolean hasSearchResponse = in.readBoolean();
            if (hasSearchResponse) {
                this.searchResponse = new SearchResponse();
                this.searchResponse.readFrom(in);
            }
        }

        @Override
        public String toString() {
            return org.forward.entitysearch.esdocumentsearch.ToString.objectToJson(this);
        }
    }

    /**
     * A {@link TransportAction} for {@link ClusteringAction}.
     */
    public static class TransportClusteringAction extends
            TransportAction<ClusteringAction.ClusteringActionRequest, ClusteringAction.ClusteringActionResponse> {
        private final Set<String> langCodeWarnings = new CopyOnWriteArraySet<>();

        private final TransportSearchAction searchAction;

        @Inject
        public TransportClusteringAction(Settings settings, ThreadPool threadPool, TransportService transportService,
                                         TransportSearchAction searchAction, ActionFilters actionFilters,
                                         IndexNameExpressionResolver indexNameExpressionResolver, NamedXContentRegistry xContentRegistry) {
            super(settings, ClusteringAction.NAME, threadPool, actionFilters, indexNameExpressionResolver,
                    transportService.getTaskManager());
            this.searchAction = searchAction;
        }

        @Override
        protected void doExecute(final ClusteringActionRequest clusteringRequest,
                                 final ActionListener<ClusteringActionResponse> listener) {
            final long tsSearchStart = System.nanoTime();
            searchAction.execute(clusteringRequest.getSearchRequest(), new ActionListener<SearchResponse>() {
                @Override
                public void onFailure(Exception e) {
                    listener.onFailure(e);
                }

                @Override
                public void onResponse(SearchResponse response) {
                    final long tsSearchEnd = System.nanoTime();
                    listener.onResponse(new ClusteringActionResponse(response));
                }
            });
        }
    }

    /**
     * An {@link BaseRestHandler} for {@link ClusteringAction}.
     */
    public static class RestClusteringAction extends BaseRestHandler {
        /**
         * Action name suffix.
         */
        public static String NAME = "_es_document_search";

        public RestClusteringAction(Settings settings, RestController controller) {
            super(settings);
            controller.registerHandler(POST, "/" + NAME, this);
            controller.registerHandler(POST, "/{index}/" + NAME, this);
            controller.registerHandler(POST, "/{index}/{type}/" + NAME, this);

            controller.registerHandler(GET, "/" + NAME, this);
            controller.registerHandler(GET, "/{index}/" + NAME, this);
            controller.registerHandler(GET, "/{index}/{type}/" + NAME, this);
        }

        @Override
        public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
            // A POST request must have a body.
            if (request.method() == POST && !request.hasContent()) {
                return channel -> emitErrorResponse(channel, logger,
                        new IllegalArgumentException("Request body was expected for a POST request."));
            }

            // Contrary to ES's default search handler we will not support
            // GET requests with a body (this is against HTTP spec guidance
            // in my opinion -- GET requests should be URL-based).
            if (request.method() == GET && request.hasContent()) {
                return channel -> emitErrorResponse(channel, logger,
                        new IllegalArgumentException("Request body was unexpected for a GET request."));
            }

            // Build an action request with data from the request.

            // Parse incoming arguments depending on the HTTP method used to make
            // the request.
            final ClusteringActionRequestBuilder actionBuilder = new ClusteringActionRequestBuilder(client);
            SearchRequest searchRequest = new SearchRequest();
            switch (request.method()) {
                case POST:
                    searchRequest.indices(Strings.splitStringByCommaToArray(request.param("index")));
                    searchRequest.types(Strings.splitStringByCommaToArray(request.param("type")));
                    actionBuilder.setSearchRequest(searchRequest);
                    actionBuilder.setSource(request.content(), request.getXContentRegistry());
                    break;

                case GET:
                    RestSearchAction.parseSearchRequest(searchRequest, request, null);

                    actionBuilder.setSearchRequest(searchRequest);
                    break;

                default:
                    throw org.forward.entitysearch.esdocumentsearch.Preconditions.unreachable();
            }

            // Dispatch clustering request.
            return channel -> client.execute(ClusteringAction.INSTANCE, actionBuilder.request(),
                    new ActionListener<ClusteringActionResponse>() {
                        @Override
                        public void onResponse(ClusteringActionResponse response) {
                            try {
                                XContentBuilder builder = channel.newBuilder();
                                builder.startObject();
                                response.toXContent(builder, request);
                                builder.endObject();
                                channel.sendResponse(
                                        new BytesRestResponse(response.getSearchResponse().status(), builder));
                            } catch (Exception e) {
                                logger.debug("Failed to emit response.", e);
                                onFailure(e);
                            }
                        }

                        @Override
                        public void onFailure(Exception e) {
                            emitErrorResponse(channel, logger, e);
                        }
                    });
        }
    }
}