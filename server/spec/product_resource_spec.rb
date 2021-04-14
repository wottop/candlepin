require 'spec_helper'
require 'candlepin_scenarios'

describe 'Product Resource' do

  include CandlepinMethods

  before do
    @owner = create_owner random_string('test_owner')

    @derived_prov_product = create_product
    @derived_product = create_product(nil, nil, { :providedProducts => [@derived_prov_product.id] })
    @prov_product = create_product
    @product = create_product(nil, nil, { :providedProducts => [@prov_product.id], :derivedProduct => @derived_product })

    @cp.create_pool(@owner['key'], @product.id, { :quantity => 10 })
  end

  it 'should fail when fetching non-existing products' do
    lambda do
      @cp.get_product_by_uuid("some bad product uuid")
    end.should raise_exception(RestClient::ResourceNotFound)
  end

  def setupOrgProductsAndPools()
    owner1 = create_owner(random_string("owner-Px"))
    owner2 = create_owner(random_string("owner-Py"))
    owner3 = create_owner(random_string("owner-Pz"))

    prod1o1 = create_product("p1", "p1", { :owner => owner1['key'] })
    prod1o2 = create_product("p1", "p1", { :owner => owner2['key'] })
    prod1o3 = create_product("p1", "p1", { :owner => owner3['key'] })

    prod2o1 = create_product("p2", "p2", { :owner => owner1['key'] })
    prod2o2 = create_product("p2", "p2", { :owner => owner2['key'] })

    prod3o2 = create_product("p3", "p3", { :owner => owner2['key'] })
    prod3o3 = create_product("p3", "p3", { :owner => owner3['key'] })

    prod4d = create_product("p4d", "p4d", {
      :owner => owner1['key'],
      :providedProducts => [prod2o1.id]
    })

    prod4 = create_product("p4", "p4", {
      :owner => owner1['key'],
      :derivedProduct => prod4d,
      :providedProducts => [prod1o1.id]
    })

    prod5d = create_product("p5d", "p5d", {
      :owner => owner2['key'],
      :providedProducts => [prod3o2.id]
    })

    prod5 = create_product("p5", "p5", {
      :owner => owner2['key'],
      :derivedProduct => prod5d,
      :providedProducts => [prod1o2.id, prod2o2.id]
    })

    prod6d = create_product("p6d", "p6d", {
      :owner => owner3['key'],
      :providedProducts => [prod3o3.id]
    })

    prod6 = create_product("p6", "p6", {
      :owner => owner3['key'],
      :derivedProduct => prod6d,
      :providedProducts => [prod1o3.id]
    })

    @cp.create_pool(owner1['key'], "p4");
    @cp.create_pool(owner2['key'], "p5");
    @cp.create_pool(owner3['key'], "p6");

    return [owner1, owner2, owner3]
  end

  it 'retrieves owners by product' do
    owners = setupOrgProductsAndPools()
    owner1 = owners[0]
    owner2 = owners[1]
    owner3 = owners[2]

    result = @cp.get_owners_with_product(["p4"])
    result.should_not be_nil
    result.length.should == 1
    result[0]['key'].should == owner1['key']

    result = @cp.get_owners_with_product(["p5d"])
    result.should_not be_nil
    result.length.should == 1
    result[0]['key'].should == owner2['key']

    result = @cp.get_owners_with_product(["p1"])
    result.should_not be_nil
    result.length.should == 3

    [owner1, owner2, owner3].each do |owner|
      found = false
      result.each do |recv|
        if recv['key'] == owner['key'] then
          found = true
          break
        end
      end

      found.should == true
    end

    result = @cp.get_owners_with_product(["p3"])
    result.should_not be_nil
    result.length.should == 2

    [owner2, owner3].each do |owner|
      found = false
      result.each do |recv|
        if recv['key'] == owner['key'] then
          found = true
          break
        end
      end

      found.should == true
    end

    result = @cp.get_owners_with_product(["p4", "p6"])
    result.should_not be_nil
    result.length.should == 2

    [owner1, owner3].each do |owner|
      found = false
      result.each do |recv|
        if recv['key'] == owner['key'] then
          found = true
          break
        end
      end

      found.should == true
    end

    result = @cp.get_owners_with_product(["nope"])
    result.should_not be_nil
    result.length.should == 0
  end

  it "refreshes pools for orgs owning products" do
    skip("candlepin running in standalone mode") if not is_hosted?

    owners = setupOrgProductsAndPools()
    owner1 = owners[0]
    owner2 = owners[1]
    owner3 = owners[2]

    # Override enabled to true:
    jobs = @cp.refresh_pools_for_orgs_with_product(["p4"])
    jobs.length.should == 1
    jobs.each do |job|
      expect(job['name']).to include("Refresh Pools")
      wait_for_job(job['id'], 15)
    end

    jobs = @cp.refresh_pools_for_orgs_with_product(["p5d"])
    jobs.length.should == 1
    jobs.each do |job|
      expect(job['name']).to include("Refresh Pools")
      wait_for_job(job['id'], 15)
    end

    jobs = @cp.refresh_pools_for_orgs_with_product(["p1"])
    jobs.length.should == 3
    jobs.each do |job|
      expect(job['name']).to include("Refresh Pools")
      wait_for_job(job['id'], 15)
    end

    jobs = @cp.refresh_pools_for_orgs_with_product(["p3"])
    jobs.length.should == 2
    jobs.each do |job|
      expect(job['name']).to include("Refresh Pools")
      wait_for_job(job['id'], 15)
    end

    jobs = @cp.refresh_pools_for_orgs_with_product(["p4", "p6"])
    jobs.length.should == 2
    jobs.each do |job|
      expect(job['name']).to include("Refresh Pools")
      wait_for_job(job['id'], 15)
    end

    jobs = @cp.refresh_pools_for_orgs_with_product(["nope"])
    jobs.length.should == 0
  end

  it 'throws exception on get_owners with no products' do
    lambda do
      @cp.get("/products/owners")
    end.should raise_exception(RestClient::BadRequest)
  end

  it 'throws exception on refresh with no products' do
    lambda do
      @cp.put("/products/subscriptions", {})
    end.should raise_exception(RestClient::BadRequest)
  end

  it "censors owner information on owner-agnostic retrieval" do
    prod_id = "test_prod"

    owner1 = create_owner(random_string("test_owner_1"))
    owner2 = create_owner(random_string("test_owner_2"))
    owner3 = create_owner(random_string("test_owner_3"))

    prod1 = create_product(prod_id, "test product", {:owner => owner1['key']})
    prod2 = create_product(prod_id, "test product", {:owner => owner2['key']})
    prod3 = create_product(prod_id, "test product", {:owner => owner3['key']})

    result = @cp.get("/products/#{prod1['uuid']}")
    result.should_not be_nil
    result["uuid"].should eq(prod1["uuid"])

    result = @cp.get("/products/#{prod2['uuid']}")
    result.should_not be_nil
    result["uuid"].should eq(prod2["uuid"])

    result = @cp.get("/products/#{prod3['uuid']}")
    result.should_not be_nil
    result["uuid"].should eq(prod3["uuid"])
  end

end

